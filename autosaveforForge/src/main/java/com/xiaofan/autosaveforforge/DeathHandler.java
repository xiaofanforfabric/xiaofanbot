package com.xiaofan.autosaveforforge;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 死亡处理
 * 通过 Tick 事件检测玩家死亡，死亡时立即停止所有 Baritone 任务和宏
 * 适用于服务器设置了立即重生的场景（没有死亡菜单）
 */
@Mod.EventBusSubscriber(modid = Autosaveforforge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class DeathHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean wasDead = false; // 上次检查时的死亡状态
    
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.level == null) {
                wasDead = false;
                return;
            }
            
            // 确保在服务器模式下（不是单人存档）
            // 在单人存档中，mc.getCurrentServer() 返回 null
            // 在服务器模式下，mc.getCurrentServer() 不为 null 或 mc.getConnection() 不为 null
            if (mc.isSingleplayer()) {
                // 单人存档，跳过检测
                wasDead = false;
                return;
            }
            
            // 检查是否连接到服务器
            if (mc.getConnection() == null) {
                wasDead = false;
                return;
            }
            
            // 检查玩家是否死亡（服务器模式下的死亡状态）
            boolean isDead = mc.player.isDeadOrDying();
            
            // 检测从存活到死亡的状态变化
            if (!wasDead && isDead) {
                LOGGER.info("[死亡处理] 检测到玩家死亡（服务器模式），正在停止所有 Baritone 任务和宏");
                
                // 在主游戏线程中执行
                mc.execute(() -> {
                    try {
                        stopAllBaritoneTasks(mc);
                    } catch (Exception e) {
                        LOGGER.error("[死亡处理] 停止 Baritone 任务时出错", e);
                    }
                });
            }
            
            // 更新状态
            wasDead = isDead;
            
        } catch (Exception e) {
            LOGGER.error("[死亡处理] 检测死亡状态时出错", e);
        }
    }
    
    /**
     * 停止所有 Baritone 任务和宏
     */
    private static void stopAllBaritoneTasks(Minecraft mc) {
        try {
            LOGGER.info("[死亡处理] 正在停止所有 Baritone 任务和宏...");
            
            // 1. 停止所有正在运行的宏
            BaritoneTaskManager.getInstance().stopAllMacros();
            LOGGER.info("[死亡处理] 已停止所有宏");
            
            // 2. 执行 Baritone 的 stop 命令，停止所有 Baritone 任务
            try {
                IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                if (baritone != null) {
                    baritone.getCommandManager().execute("stop");
                    LOGGER.info("[死亡处理] 已执行 Baritone stop 命令");
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(Component.literal("§7[死亡处理] 已停止所有 Baritone 任务和宏"));
                    }
                } else {
                    LOGGER.debug("[死亡处理] Baritone 未加载，跳过 stop 命令");
                }
            } catch (Exception e) {
                LOGGER.warn("[死亡处理] 执行 Baritone stop 命令失败: {}", e.getMessage());
            }
            
        } catch (Exception e) {
            LOGGER.error("[死亡处理] 停止 Baritone 任务时出错", e);
        }
    }
}

