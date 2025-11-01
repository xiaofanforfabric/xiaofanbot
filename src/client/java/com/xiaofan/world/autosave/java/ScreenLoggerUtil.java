package com.xiaofan.world.autosave.java;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 独立的屏幕日志显示工具类
 * 无需修改主类，直接使用 ScreenLoggerUtil.init() 初始化
 */
public class ScreenLoggerUtil {

    // 配置常量
    public static final int MAX_LINES = 20;
    public static final long LOG_TIMEOUT_MS = 30000; // 30秒后自动清理
    public static final int DEFAULT_TOGGLE_KEY = GLFW.GLFW_KEY_F10;

    // 状态变量
    private static boolean enabled = false;
    private static final CopyOnWriteArrayList<LogMessage> logMessages = new CopyOnWriteArrayList<>();
    private static KeyBinding toggleKeyBinding;
    private static Appender logAppender;

    /**
     * 初始化屏幕日志系统
     * 在你的客户端初始化代码中调用此方法
     */
    public static void init() {
        registerKeyBinding();
        registerTickListener();
        registerRenderListener();
        setupLogCapture();

        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(
                        Text.literal("屏幕日志功能已加载。按 F10 切换显示").formatted(Formatting.GRAY),
                        false
                );
            }
        });
    }

    /**
     * 启用或禁用屏幕日志显示
     */
    public static void setEnabled(boolean enabled) {
        ScreenLoggerUtil.enabled = enabled;
    }

    /**
     * 切换屏幕日志显示状态
     */
    public static void toggle() {
        enabled = !enabled;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(
                    Text.literal("屏幕日志: " + (enabled ? "§a启用" : "§c禁用")).formatted(Formatting.GRAY),
                    false
            );
        }
    }

    /**
     * 清空所有日志消息
     */
    public static void clearLogs() {
        logMessages.clear();
    }

    /**
     * 获取当前是否启用
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * 获取当前日志数量
     */
    public static int getLogCount() {
        return logMessages.size();
    }

    // ========== 私有实现方法 ==========

    private static void registerKeyBinding() {
        toggleKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.yourmod.screen_logger.toggle",
                InputUtil.Type.KEYSYM,
                DEFAULT_TOGGLE_KEY,
                "category.yourmod.utils"
        ));
    }

    private static void registerTickListener() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 检测切换按键
            while (toggleKeyBinding.wasPressed()) {
                toggle();
            }

            // 定期清理旧日志
            if (client.world != null && client.world.getTime() % 100 == 0) {
                cleanupOldLogs();
            }
        });
    }

    private static void registerRenderListener() {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!enabled || logMessages.isEmpty()) return;
            renderLogs(drawContext);
        });
    }

    private static void renderLogs(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        int y = 30;
        int maxWidth = context.getScaledWindowWidth() - 20;

        for (LogMessage log : logMessages) {
            if (y > context.getScaledWindowHeight() - 30) break;

            int textColor = getColorForLevel(log.level);
            String displayText = "[" + log.level.name() + "] " + log.message;

            // 处理过长文本
            if (textRenderer.getWidth(displayText) > maxWidth) {
                displayText = textRenderer.trimToWidth(displayText, maxWidth - 20) + "...";
            }

            // 绘制背景和文本
            int textWidth = textRenderer.getWidth(displayText);
            context.fill(10, y - 2, 12 + textWidth, y + 10, 0x80000000);
            context.drawText(textRenderer, displayText, 11, y, textColor, false);

            y += 11;
        }
    }

    private static void cleanupOldLogs() {
        long currentTime = System.currentTimeMillis();
        logMessages.removeIf(log -> currentTime - log.timestamp > LOG_TIMEOUT_MS);

        while (logMessages.size() > MAX_LINES) {
            logMessages.remove(0);
        }
    }

    private static void setupLogCapture() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();
        LoggerConfig rootLogger = config.getRootLogger();

        logAppender = new AbstractAppender("ScreenLoggerAppender", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (!enabled) return;

                String message = event.getMessage().getFormattedMessage();
                Level level = event.getLevel();

                MinecraftClient.getInstance().execute(() -> {
                    logMessages.add(new LogMessage(message, level));
                    if (logMessages.size() > MAX_LINES) {
                        logMessages.remove(0);
                    }
                });
            }
        };

        logAppender.start();
        rootLogger.addAppender(logAppender, Level.ALL, null);
        context.updateLoggers();
    }

    private static int getColorForLevel(Level level) {
        if (level == Level.ERROR || level == Level.FATAL) return 0xFF5555;
        if (level == Level.WARN) return 0xFFAA00;
        if (level == Level.INFO) return 0x55FF55;
        if (level == Level.DEBUG) return 0x5555FF;
        if (level == Level.TRACE) return 0xFF55FF;
        return 0xAAAAAA;
    }

    // 内部日志消息类
    private static class LogMessage {
        public final String message;
        public final Level level;
        public final long timestamp;

        public LogMessage(String message, Level level) {
            this.message = message;
            this.level = level;
            this.timestamp = System.currentTimeMillis();
        }
    }
}