package com.xiaofan.qqbot.handler;

import com.xiaofan.qqbot.manager.SilentModeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiFunction;

/**
 * 群聊静默模式处理器
 * 处理"群聊静默 开启"和"群聊静默 关闭"命令（仅管理员可用）
 */
public class SilentModeHandler {
    private static final Logger logger = LoggerFactory.getLogger(SilentModeHandler.class);
    
    // 管理员QQ号
    private static final long ADMIN_QQ_ID = 2183576276L;
    
    // 触发词（严格匹配）
    private static final String SILENT_ENABLE_TRIGGER = "群聊静默 开启";
    private static final String SILENT_DISABLE_TRIGGER = "群聊静默 关闭";
    
    private final BiFunction<Long, String, Boolean> messageSender;
    private final SilentModeManager silentModeManager;
    
    /**
     * 构造函数
     * @param messageSender 消息发送函数
     * @param silentModeManager 静默模式管理器
     */
    public SilentModeHandler(BiFunction<Long, String, Boolean> messageSender,
                           SilentModeManager silentModeManager) {
        this.messageSender = messageSender;
        this.silentModeManager = silentModeManager;
    }
    
    /**
     * 检查是否是管理员
     * @param userId 用户QQ号
     * @return 如果是管理员返回true，否则返回false
     */
    private boolean isAdmin(long userId) {
        return userId == ADMIN_QQ_ID;
    }
    
    /**
     * 检查是否应该处理消息
     * @param messageText 消息内容
     * @return 如果应该处理返回true，否则返回false
     */
    public boolean shouldHandle(String messageText) {
        if (messageText == null) {
            return false;
        }
        
        String trimmed = messageText.trim();
        return trimmed.equals(SILENT_ENABLE_TRIGGER) || 
               trimmed.equals(SILENT_DISABLE_TRIGGER);
    }
    
    /**
     * 处理群聊静默命令
     * @param groupId 群号
     * @param userId 用户QQ号
     * @param messageText 消息内容
     */
    public void handleSilentCommand(long groupId, long userId, String messageText) {
        if (messageText == null) {
            return;
        }
        
        // 检查管理员权限
        if (!isAdmin(userId)) {
            logger.warn("非管理员尝试使用群聊静默命令，用户: {}", userId);
            messageSender.apply(groupId, "权限不足");
            return;
        }
        
        String trimmed = messageText.trim();
        
        // 处理"群聊静默 开启"
        if (trimmed.equals(SILENT_ENABLE_TRIGGER)) {
            handleEnableSilent(groupId, userId);
            return;
        }
        
        // 处理"群聊静默 关闭"
        if (trimmed.equals(SILENT_DISABLE_TRIGGER)) {
            handleDisableSilent(groupId, userId);
            return;
        }
    }
    
    /**
     * 处理"群聊静默 开启"命令
     */
    private void handleEnableSilent(long groupId, long userId) {
        logger.info("管理员 {} 请求开启群 {} 的静默模式", userId, groupId);
        
        boolean success = silentModeManager.enableSilent(groupId);
        
        if (success) {
            messageSender.apply(groupId, "已开启群聊静默模式");
            logger.info("管理员 {} 成功开启群 {} 的静默模式", userId, groupId);
        } else {
            messageSender.apply(groupId, "群聊静默模式已处于开启状态");
            logger.info("管理员 {} 尝试开启群 {} 的静默模式，但已在静默模式中", userId, groupId);
        }
    }
    
    /**
     * 处理"群聊静默 关闭"命令
     */
    private void handleDisableSilent(long groupId, long userId) {
        logger.info("管理员 {} 请求关闭群 {} 的静默模式", userId, groupId);
        
        boolean success = silentModeManager.disableSilent(groupId);
        
        if (success) {
            messageSender.apply(groupId, "已关闭群聊静默模式");
            logger.info("管理员 {} 成功关闭群 {} 的静默模式", userId, groupId);
        } else {
            messageSender.apply(groupId, "群聊静默模式已处于关闭状态");
            logger.info("管理员 {} 尝试关闭群 {} 的静默模式，但不在静默模式中", userId, groupId);
        }
    }
}
