package com.xiaofan.autosaveforforge;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 标题屏幕检测器
 * 当游戏加载完毕进入标题屏幕时，在日志中输出提示信息
 * 用于帮助判断模组是否加载完毕（在模组较多的情况下加载很慢）
 */
@Mod.EventBusSubscriber(modid = Autosaveforforge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class TitleScreenDetector {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean hasLoggedTitleScreen = false;
    
    /**
     * 监听客户端 Tick 事件
     * 在每个客户端 tick 中检查当前屏幕是否是标题屏幕
     * 当检测到标题屏幕时，输出日志提示
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // 只在 tick 结束时检查（避免重复检查）
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // 只在第一次检测到标题屏幕时输出日志
        if (!hasLoggedTitleScreen) {
            Minecraft mc = Minecraft.getInstance();
            // 检查当前屏幕是否是标题屏幕
            if (mc != null && mc.screen instanceof TitleScreen) {
                // 输出日志，提示用户已进入标题屏幕
                LOGGER.info("========================================");
                LOGGER.info("标题屏幕，使用鼠标或Tab键选择控件");
                LOGGER.info("========================================");
                hasLoggedTitleScreen = true;
            }
        }
    }
    
    /**
     * 重置标志（当退出到标题屏幕时，可以再次输出）
     * 这会在游戏关闭或重新加载时被调用
     */
    public static void reset() {
        hasLoggedTitleScreen = false;
    }
}
