package com.xiaofan.world.autosave.java;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimedMessageSender {
    private static final Logger LOGGER = LoggerFactory.getLogger("autosave-timer");
    private static int tickCounter = 0;
    private static final int TICKS_PER_MINUTE = 20 * 60; // 20 ticks/秒 × 60 秒

    // 消息内容（不包含命令前缀）
    private static final String MESSAGE_CONTENT = "大家好，这里是xiaofan，我本人已经上学了，这是一个自动化模组（此模组用于自动恢复疆土民心）所说的话。即日起，雅典维亚城邦将对全服国家开放访问权限，欢迎他国盟友前来参观墓园，和我们的家园。另外，请不要在我们都去上学时来一个宣战，谢谢。（本消息一分钟自动发送一次，如果打扰到你，使用/c mute xiaofan屏蔽，谢谢）。";

    /**
     * 初始化定时消息发送器
     */
    public static void initialize() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            // 只在游戏世界中计时
            if (client.world == null) {
                tickCounter = 0;
                return;
            }

            tickCounter++;

            if (tickCounter >= TICKS_PER_MINUTE) {
                executeTimedMessage();
                tickCounter = 0;
                LOGGER.info("计时器已重置，开始新一轮计时");
            }

            // 可选：每30秒记录一次计时器状态（调试用）
            if (tickCounter % 600 == 0 && tickCounter > 0) {
                LOGGER.debug("计时器状态: {}", getTimerStatus());
            }
        });

        LOGGER.info("定时消息发送器已初始化，将每分钟发送一次消息");
    }

    /**
     * 执行定时消息发送
     */
    private static void executeTimedMessage() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;

        try {
            if (player != null && player.networkHandler != null && client.world != null) {
                // 方案1：直接发送到c频道（使用命令）
                player.networkHandler.sendChatCommand("c " + MESSAGE_CONTENT);

                // 方案2：或者直接发送聊天消息（如果服务器支持c前缀）
                // player.networkHandler.sendChatMessage("c " + MESSAGE_CONTENT);

                LOGGER.info("定时消息已发送");
            } else {
                LOGGER.warn("无法发送定时消息: 玩家未连接到服务器");
            }
        } catch (Exception e) {
            LOGGER.error("发送定时消息时出错: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取当前计时器状态（用于调试和测试）
     */
    public static String getTimerStatus() {
        int totalSeconds = tickCounter / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        int progress = (tickCounter * 100) / TICKS_PER_MINUTE;

        return String.format("%d分%d秒 (%d%%)", minutes, seconds, progress);
    }

    /**
     * 手动触发消息发送（用于测试）
     */
    public static void sendMessageNow() {
        LOGGER.info("手动触发消息发送");
        executeTimedMessage();
    }

    /**
     * 重置计时器
     */
    public static void resetTimer() {
        tickCounter = 0;
        LOGGER.info("计时器已重置");
    }

    /**
     * 获取剩余时间（秒）
     */
    public static int getRemainingSeconds() {
        return (TICKS_PER_MINUTE - tickCounter) / 20;
    }

    /**
     * 检查是否接近发送时间（用于UI显示等）
     */
    public static boolean isAlmostTime() {
        return tickCounter >= TICKS_PER_MINUTE - 100; // 最后5秒
    }
}