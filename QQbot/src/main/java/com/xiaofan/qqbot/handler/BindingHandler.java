package com.xiaofan.qqbot.handler;

import com.xiaofan.qqbot.manager.BindingSessionManager;
import com.xiaofan.qqbot.manager.DatabaseManager;
import com.xiaofan.qqbot.send.KookMessageSender;
import com.xiaofan.qqbot.send.QQMessageSender;
import com.xiaofan.qqbot.websocket.BindingAPIServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiFunction;

/**
 * 绑定处理器
 * 处理"绑定"触发词，管理游戏ID绑定流程
 */
public class BindingHandler {
    private static final Logger logger = LoggerFactory.getLogger(BindingHandler.class);
    
    private static final String TRIGGER_KEYWORD = "绑定";
    
    private final DatabaseManager databaseManager;
    private final BiFunction<Long, String, Boolean> messageSender;
    private final QQMessageSender qqMessageSender;
    private final KookMessageSender kookMessageSender;
    private final BindingSessionManager sessionManager;
    private final BindingAPIServer bindingAPIServer;
    
    public BindingHandler(BiFunction<Long, String, Boolean> messageSender,
                          BindingSessionManager sessionManager,
                          BindingAPIServer bindingAPIServer) {
        this.messageSender = messageSender;
        this.sessionManager = sessionManager;
        this.bindingAPIServer = bindingAPIServer;
        this.databaseManager = new DatabaseManager();
        this.qqMessageSender = null;
        this.kookMessageSender = null;
    }
    
    public BindingHandler(QQMessageSender qqMessageSender,
                          KookMessageSender kookMessageSender,
                          BindingSessionManager sessionManager,
                          BindingAPIServer bindingAPIServer) {
        this.qqMessageSender = qqMessageSender;
        this.kookMessageSender = kookMessageSender;
        this.messageSender = null;
        this.sessionManager = sessionManager;
        this.bindingAPIServer = bindingAPIServer;
        this.databaseManager = new DatabaseManager();
    }
    
    /**
     * 检查是否应该处理消息
     */
    public boolean shouldHandle(String messageText) {
        if (messageText == null) {
            return false;
        }
        return messageText.trim().equals(TRIGGER_KEYWORD);
    }
    
    /**
     * 处理绑定命令（QQ消息）
     * @param groupId 群号
     * @param userId 用户QQ号
     * @param messageText 消息内容
     */
    public void handleBinding(long groupId, long userId, String messageText) {
        if (!shouldHandle(messageText)) {
            return;
        }
        
        logger.info("检测到绑定请求，群号: {}, 用户: {}", groupId, userId);
        
        // 检查是否已绑定
        String existingGameId = databaseManager.getGameId(userId);
        if (existingGameId != null && !existingGameId.isEmpty()) {
            if (qqMessageSender != null) {
                qqMessageSender.sendGroupMessage(groupId, String.format("您已经绑定了游戏ID: %s", existingGameId));
            } else if (messageSender != null) {
                messageSender.apply(groupId, String.format("您已经绑定了游戏ID: %s", existingGameId));
            }
            return;
        }
        
        // 生成验证码并创建会话
        String passCode = sessionManager.startSession(userId, groupId);
        logger.info("为用户 {} 生成绑定验证码: {}", userId, passCode);
        
        if (passCode == null) {
            if (qqMessageSender != null) {
                qqMessageSender.sendGroupMessage(groupId, "绑定失败：已有其他用户正在绑定，请稍后再试");
            } else if (messageSender != null) {
                messageSender.apply(groupId, "绑定失败：已有其他用户正在绑定，请稍后再试");
            }
            return;
        }
        
        // 启用API服务器（如果未启用）
        if (!bindingAPIServer.isRunning()) {
            bindingAPIServer.initialize();
            logger.info("绑定API服务器已启动");
        }
        
        // 启用API
        bindingAPIServer.enable();
        
        String message = String.format(
            "请按照以下步骤完成绑定：\n" +
            "1. 在游戏中输入以下命令：/qqbot pass_code %s\n" +
            "2. 等待验证完成\n" +
            "验证码有效期：5分钟",
            passCode
        );
        
        if (qqMessageSender != null) {
            qqMessageSender.sendGroupMessage(groupId, message);
        } else if (messageSender != null) {
            messageSender.apply(groupId, message);
        }
    }
    
    /**
     * 处理绑定命令（KOOK消息）
     * @param channelId 频道ID
     * @param userId 用户ID（字符串）
     * @param messageText 消息内容
     */
    public void handleBindingKook(String channelId, String userId, String messageText) {
        if (!shouldHandle(messageText)) {
            return;
        }
        
        logger.info("检测到绑定请求（KOOK），频道: {}, 用户: {}", channelId, userId);
        
        // 将KOOK的userId转换为long（KOOK的userId是数字字符串）
        long userIdLong = 0;
        try {
            userIdLong = Long.parseLong(userId);
        } catch (NumberFormatException e) {
            logger.error("无法将KOOK用户ID转换为long: {}", userId);
            if (kookMessageSender != null) {
                kookMessageSender.sendChannelMessage(channelId, "绑定失败：无效的用户ID");
            }
            return;
        }
        
        // 检查是否已绑定
        String existingGameId = databaseManager.getGameId(userIdLong);
        if (existingGameId != null && !existingGameId.isEmpty()) {
            if (kookMessageSender != null) {
                kookMessageSender.sendChannelMessage(channelId, String.format("您已经绑定了游戏ID: %s", existingGameId));
            }
            return;
        }
        
        // 生成验证码并创建会话（使用0作为groupId，因为KOOK使用channelId）
        String passCode = sessionManager.startSession(userIdLong, 0);
        logger.info("为用户 {} 生成绑定验证码: {}", userId, passCode);
        
        if (passCode == null) {
            if (kookMessageSender != null) {
                kookMessageSender.sendChannelMessage(channelId, "绑定失败：已有其他用户正在绑定，请稍后再试");
            }
            return;
        }
        
        // 启用API服务器（如果未启用）
        if (!bindingAPIServer.isRunning()) {
            bindingAPIServer.initialize();
            logger.info("绑定API服务器已启动");
        }
        
        // 启用API
        bindingAPIServer.enable();
        
        String message = String.format(
            "请按照以下步骤完成绑定：\n" +
            "1. 在游戏中输入以下命令：/qqbot pass_code %s\n" +
            "2. 等待验证完成\n" +
            "验证码有效期：5分钟",
            passCode
        );
        
        if (kookMessageSender != null) {
            kookMessageSender.sendChannelMessage(channelId, message);
        }
    }
}
