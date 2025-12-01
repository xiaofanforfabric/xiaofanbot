package com.xiaofan.autosaveforforge;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 自动睡觉控制器
 * 当游戏时间到了可以睡觉的时间段时，自动尝试在附近的床上睡觉
 */
@Mod.EventBusSubscriber(modid = Autosaveforforge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class AutoSleepController {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // 可以睡觉的时间范围（游戏时间，0-24000为一个完整的一天）
    // 12500 = 晚上7点，23450 = 早上6点
    private static final long SLEEP_START_TIME = 12500L;  // 晚上7点
    private static final long SLEEP_END_TIME = 23450L;    // 早上6点
    
    // 搜索床的范围（以玩家为中心）
    private static final int SEARCH_RANGE = 8;
    
    // 防止频繁尝试睡觉的冷却时间（tick数，20 tick = 1秒）
    private static final int COOLDOWN_TICKS = 40; // 2秒
    
    private static int cooldownCounter = 0;
    private static boolean lastSleepAttemptFailed = false;
    
    /**
     * 监听客户端 Tick 事件
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // 只在 tick 结束时检查
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        
        // 冷却时间计数
        if (cooldownCounter > 0) {
            cooldownCounter--;
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) {
            return;
        }
        
        LocalPlayer player = mc.player;
        
        // 检查是否已经在睡觉
        if (player.isSleeping()) {
            return;
        }
        
        // 检查是否在游戏中（不在菜单）
        if (mc.level == null) {
            return;
        }
        
        // 获取游戏时间（dayTime：0-24000，0是早上6点）
        long dayTime = mc.level.getDayTime() % 24000;
        
        // 检查是否在可以睡觉的时间段
        boolean canSleep = false;
        if (dayTime >= SLEEP_START_TIME || dayTime < SLEEP_END_TIME) {
            canSleep = true;
        }
        
        if (!canSleep) {
            // 不在睡觉时间段，重置失败标志
            lastSleepAttemptFailed = false;
            return;
        }
        
        // 如果上次尝试失败，增加冷却时间
        if (lastSleepAttemptFailed) {
            cooldownCounter = COOLDOWN_TICKS * 2; // 失败后等待更长时间
            lastSleepAttemptFailed = false;
            return;
        }
        
        // 尝试找床并睡觉
        BlockPos bedPos = findNearbyBed(player);
        if (bedPos != null) {
            try {
                // 移动到床附近（如果距离太远）
                Vec3 playerPos = player.position();
                Vec3 bedVec = Vec3.atCenterOf(bedPos);
                double distance = playerPos.distanceTo(bedVec);
                
                if (distance > 3.0) {
                    // 床太远，尝试靠近（简单的移动逻辑）
                    // 这里只是记录日志，实际移动可能需要更复杂的路径查找
                    LOGGER.debug("[自动睡觉] 床距离较远 ({} 格)，尝试靠近", String.format("%.1f", distance));
                }
                
                // 尝试在床上睡觉
                if (mc.getConnection() != null && mc.getConnection().getConnection() != null && mc.gameMode != null) {
                    mc.execute(() -> {
                        try {
                            // 获取床的方块状态
                            BlockState bedState = mc.level.getBlockState(bedPos);
                            Block block = bedState.getBlock();
                            
                            // 检查是否是床
                            if (block instanceof BedBlock) {
                                // 检查距离是否足够近（床的交互范围通常是3格）
                                if (distance <= 3.0) {
                                    LOGGER.info("[自动睡觉] 尝试在床 ({}, {}, {}) 上睡觉，距离: {} 格", 
                                        bedPos.getX(), bedPos.getY(), bedPos.getZ(), String.format("%.1f", distance));
                                    
                                    // 计算床的中心位置（用于交互）
                                    Vec3 bedCenter = Vec3.atCenterOf(bedPos);
                                    
                                    // 创建 BlockHitResult（用于交互）
                                    // 使用床的上表面中心作为点击位置
                                    Vec3 hitVec = bedCenter.add(0, 0.5, 0);
                                    BlockHitResult hitResult = new BlockHitResult(
                                        hitVec,
                                        Direction.UP, // 从上方点击
                                        bedPos,
                                        false // 不是内部点击
                                    );
                                    
                                    // 使用 GameMode 的 useItemOn 方法与床交互
                                    // 这会发送数据包到服务器，服务器会处理睡觉逻辑
                                    // 注意：useItemOn 需要 LocalPlayer 类型
                                    InteractionResult result = mc.gameMode.useItemOn(
                                        player,
                                        InteractionHand.MAIN_HAND,
                                        hitResult
                                    );
                                    
                                    if (result.consumesAction()) {
                                        LOGGER.info("[自动睡觉] 成功尝试在床上睡觉");
                                        lastSleepAttemptFailed = false;
                                    } else {
                                        LOGGER.debug("[自动睡觉] 无法在床上睡觉（可能床被占用或距离太远）");
                                        lastSleepAttemptFailed = true;
                                    }
                                } else {
                                    LOGGER.debug("[自动睡觉] 床距离太远 ({} 格)，需要靠近", String.format("%.1f", distance));
                                    lastSleepAttemptFailed = true;
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.error("[自动睡觉] 尝试睡觉时发生错误", e);
                            lastSleepAttemptFailed = true;
                            cooldownCounter = COOLDOWN_TICKS;
                        }
                    });
                }
                
                // 设置冷却时间
                cooldownCounter = COOLDOWN_TICKS;
                
            } catch (Exception e) {
                LOGGER.error("[自动睡觉] 处理睡觉逻辑时发生错误", e);
                lastSleepAttemptFailed = true;
                cooldownCounter = COOLDOWN_TICKS;
            }
        } else {
            // 没有找到床
            if (cooldownCounter == 0) {
                LOGGER.debug("[自动睡觉] 附近没有找到床（搜索范围：{}格）", SEARCH_RANGE);
                cooldownCounter = COOLDOWN_TICKS * 3; // 没有床时等待更长时间
            }
        }
    }
    
    /**
     * 在玩家附近查找床
     * @param player 玩家
     * @return 床的位置，如果没找到返回 null
     */
    private static BlockPos findNearbyBed(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return null;
        }
        
        BlockPos playerPos = player.blockPosition();
        BlockPos closestBed = null;
        double closestDistance = Double.MAX_VALUE;
        
        // 在搜索范围内查找床
        for (int x = -SEARCH_RANGE; x <= SEARCH_RANGE; x++) {
            for (int y = -SEARCH_RANGE; y <= SEARCH_RANGE; y++) {
                for (int z = -SEARCH_RANGE; z <= SEARCH_RANGE; z++) {
                    BlockPos checkPos = playerPos.offset(x, y, z);
                    BlockState state = mc.level.getBlockState(checkPos);
                    Block block = state.getBlock();
                    
                    // 检查是否是床
                    if (block instanceof BedBlock) {
                        // 检查床是否可用（没有被占用等）
                        // 注意：在客户端可能无法完全检查占用状态
                        
                        double distance = playerPos.distSqr(checkPos);
                        if (distance < closestDistance) {
                            closestDistance = distance;
                            closestBed = checkPos;
                        }
                    }
                }
            }
        }
        
        return closestBed;
    }
}

