package com.xiaofan.world.autosave.java;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatCommandExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger("autosave-chat");
    private static final String TARGET_PHRASE = "忠诚度低下";
    private static final String COMMAND = "u restore confirm";

    /**
     * 初始化客户端监听 - 使用旧版API
     */
    public static void initialize() {
        LOGGER.info("开始注册聊天监听器...");
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            LOGGER.debug("GAME消息: {}", message.getString());
            checkAndExecuteCommand(message.getString());
        });
        LOGGER.info("GAME监听器注册成功");

        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            LOGGER.debug("CHAT消息: {}", message.getString());
            checkAndExecuteCommand(message.getString());
        });
        LOGGER.info("CHAT监听器注册成功");
        try {
            // 监听游戏内消息（系统消息、公告等）
            ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
                LOGGER.debug("[GAME] 收到游戏消息: {} (overlay: {})", message.getString(), overlay);
                checkAndExecuteCommand(message.getString());
            });

            // 监听聊天消息（玩家聊天）
            ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
                LOGGER.debug("[CHAT] 收到聊天消息: {}", message.getString());
                checkAndExecuteCommand(message.getString());
            });

            // 监听所有允许的游戏消息
            ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
                LOGGER.debug("[ALLOW_GAME] 收到允许的游戏消息: {} (overlay: {})", message.getString(), overlay);
                checkAndExecuteCommand(message.getString());
                return true; // 允许消息显示
            });

            // 监听所有允许的聊天消息
            ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
                LOGGER.debug("[ALLOW_CHAT] 收到允许的聊天消息: {}", message.getString());
                checkAndExecuteCommand(message.getString());
                return true; // 允许消息显示
            });

            LOGGER.info("聊天命令监听器已初始化 (旧版API)");
        } catch (Exception e) {
            LOGGER.error("初始化监听器失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 检查聊天消息并在满足条件时执行命令
     */
    private static void checkAndExecuteCommand(String message) {
        // 移除颜色代码和格式符号，只比较纯文本内容
        String plainText = message.replaceAll("§[0-9a-fk-or]", "");

        if (plainText.contains(TARGET_PHRASE)) {
            LOGGER.info("检测到关键词 '{}'，原始消息: {}，纯文本: {}", TARGET_PHRASE, message, plainText);
            LOGGER.info("正在执行命令: {}", COMMAND);

            // 在主游戏线程中执行命令
            scheduleCommandExecution();
        }
    }

    /**
     * 在主游戏线程中安排命令执行
     */
    private static void scheduleCommandExecution() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(() -> {
                executeClientCommand(COMMAND);
            });
        }
    }

    /**
     * 执行客户端命令
     */
    private static void executeClientCommand(String command) {
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            if (client != null && client.player != null && client.getNetworkHandler() != null) {
                // 发送命令（确保没有前导斜杠）
                String cleanCommand = command.startsWith("/") ? command.substring(1) : command;
                client.getNetworkHandler().sendCommand(cleanCommand);
                LOGGER.info("命令已发送: /{}", cleanCommand);
            } else {
                LOGGER.warn("无法执行命令: 客户端未连接到服务器或玩家不存在");
            }
        } catch (Exception e) {
            LOGGER.error("执行命令时出错: {}", e.getMessage(), e);
        }
    }
}