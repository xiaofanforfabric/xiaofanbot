package com.xiaofan.qqbot.handler;

import com.xiaofan.qqbot.manager.BanListManager;
import com.xiaofan.qqbot.manager.DatabaseManager;
import com.xiaofan.qqbot.send.KookMessageSender;
import com.xiaofan.qqbot.send.QQMessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.function.BiFunction;

/**
 * 黑名单管理处理器
 * 处理黑名单相关的管理命令（仅管理员可用）
 */
public class BanHandler {
    private static final Logger logger = LoggerFactory.getLogger(BanHandler.class);
    
    // 管理员QQ号
    private static final long ADMIN_QQ_ID = 2183576276L;
    
    // 触发词
    private static final String BAN_LIST_TRIGGER = "黑名单列表";
    private static final String BAN_DELETE_TIPS_TRIGGER = "黑名单删除所有封禁用户投稿";
    private static final String BAN_ADD_PREFIX = "/ban";
    
    private final BiFunction<Long, String, Boolean> messageSender;
    private final QQMessageSender qqMessageSender;
    private final KookMessageSender kookMessageSender;
    private final BanListManager banListManager;
    private final DatabaseManager databaseManager;
    
    /**
     * 构造函数（旧版本，兼容性）
     * @param messageSender 消息发送函数
     * @param banListManager 黑名单管理器
     * @param databaseManager 数据库管理器
     */
    public BanHandler(BiFunction<Long, String, Boolean> messageSender,
                     BanListManager banListManager,
                     DatabaseManager databaseManager) {
        this.messageSender = messageSender;
        this.qqMessageSender = null;
        this.kookMessageSender = null;
        this.banListManager = banListManager;
        this.databaseManager = databaseManager;
    }
    
    /**
     * 构造函数（新版本，支持双发送器）
     * @param qqMessageSender QQ消息发送器
     * @param kookMessageSender KOOK消息发送器
     * @param banListManager 黑名单管理器
     * @param databaseManager 数据库管理器
     */
    public BanHandler(QQMessageSender qqMessageSender,
                     KookMessageSender kookMessageSender,
                     BanListManager banListManager,
                     DatabaseManager databaseManager) {
        this.qqMessageSender = qqMessageSender;
        this.kookMessageSender = kookMessageSender;
        this.messageSender = null;
        this.banListManager = banListManager;
        this.databaseManager = databaseManager;
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
        return trimmed.equals(BAN_LIST_TRIGGER) ||
               trimmed.equals(BAN_DELETE_TIPS_TRIGGER) ||
               trimmed.startsWith(BAN_ADD_PREFIX);
    }
    
    /**
     * 处理黑名单管理命令
     * @param groupId 群号
     * @param userId 用户QQ号
     * @param messageText 消息内容
     */
    public void handleBanCommand(long groupId, long userId, String messageText) {
        if (messageText == null) {
            return;
        }
        
        // 检查管理员权限
        if (!isAdmin(userId)) {
            logger.warn("非管理员尝试使用黑名单管理命令，用户: {}", userId);
            if (qqMessageSender != null) {
                qqMessageSender.sendGroupMessage(groupId, "权限不足");
            } else if (messageSender != null) {
                messageSender.apply(groupId, "权限不足");
            }
            return;
        }
        
        String trimmed = messageText.trim();
        
        // 处理"黑名单列表"
        if (trimmed.equals(BAN_LIST_TRIGGER)) {
            handleBanList(groupId, userId);
            return;
        }
        
        // 处理"黑名单删除所有封禁用户投稿"
        if (trimmed.equals(BAN_DELETE_TIPS_TRIGGER)) {
            handleDeleteBannedUserTips(groupId, userId);
            return;
        }
        
        // 处理"/ban (QQ号)"
        if (trimmed.startsWith(BAN_ADD_PREFIX)) {
            handleAddBan(groupId, userId, trimmed);
            return;
        }
    }
    
    /**
     * 处理"黑名单列表"命令
     */
    private void handleBanList(long groupId, long userId) {
        logger.info("管理员 {} 请求查看黑名单列表", userId);
        
        Set<Long> bannedUsers = banListManager.getAllBannedUsers();
        
        if (bannedUsers.isEmpty()) {
            String message = "当前黑名单为空";
            if (qqMessageSender != null) {
                qqMessageSender.sendGroupMessage(groupId, message);
            } else if (messageSender != null) {
                messageSender.apply(groupId, message);
            }
            return;
        }
        
        StringBuilder result = new StringBuilder();
        result.append("黑名单列表（共").append(bannedUsers.size()).append("人）：\n");
        
        int index = 1;
        for (Long qqId : bannedUsers) {
            result.append(index).append(". ").append(qqId);
            if (index < bannedUsers.size()) {
                result.append("\n");
            }
            index++;
        }
        
        if (qqMessageSender != null) {
            qqMessageSender.sendGroupMessage(groupId, result.toString());
        } else if (messageSender != null) {
            messageSender.apply(groupId, result.toString());
        }
        logger.info("管理员 {} 查看黑名单列表完成，共 {} 人", userId, bannedUsers.size());
    }
    
    /**
     * 处理"黑名单删除所有封禁用户投稿"命令
     */
    private void handleDeleteBannedUserTips(long groupId, long userId) {
        logger.info("管理员 {} 请求删除所有黑名单用户的投稿", userId);
        
        Set<Long> bannedUsers = banListManager.getAllBannedUsers();
        
        if (bannedUsers.isEmpty()) {
            String message = "当前黑名单为空，无需删除";
            if (qqMessageSender != null) {
                qqMessageSender.sendGroupMessage(groupId, message);
            } else if (messageSender != null) {
                messageSender.apply(groupId, message);
            }
            return;
        }
        
        String waitingMessage = "正在删除黑名单用户的投稿，请稍候...";
        if (qqMessageSender != null) {
            qqMessageSender.sendGroupMessage(groupId, waitingMessage);
        } else if (messageSender != null) {
            messageSender.apply(groupId, waitingMessage);
        }
        
        int deletedCount = databaseManager.deleteTipsByQqIds(bannedUsers);
        
        if (deletedCount < 0) {
            String errorMessage = "删除投稿时发生错误，请查看日志";
            if (qqMessageSender != null) {
                qqMessageSender.sendGroupMessage(groupId, errorMessage);
            } else if (messageSender != null) {
                messageSender.apply(groupId, errorMessage);
            }
            logger.error("管理员 {} 删除黑名单用户投稿时发生错误", userId);
            return;
        }
        
        String result = String.format("删除完成！共删除 %d 条投稿（涉及 %d 个黑名单用户）", 
                                     deletedCount, bannedUsers.size());
        if (qqMessageSender != null) {
            qqMessageSender.sendGroupMessage(groupId, result);
        } else if (messageSender != null) {
            messageSender.apply(groupId, result);
        }
        logger.info("管理员 {} 删除黑名单用户投稿完成，共删除 {} 条投稿", userId, deletedCount);
    }
    
    /**
     * 处理"/ban (QQ号)"命令
     * @param command 命令字符串，格式：/ban (QQ号)
     */
    private void handleAddBan(long groupId, long userId, String command) {
        logger.info("管理员 {} 请求拉黑用户，命令: {}", userId, command);
        
        // 解析命令：/ban (QQ号)
        String[] parts = command.split("\\s+");
        if (parts.length < 2) {
            String message = "命令格式错误，正确格式：/ban (QQ号)";
            if (qqMessageSender != null) {
                qqMessageSender.sendGroupMessage(groupId, message);
            } else if (messageSender != null) {
                messageSender.apply(groupId, message);
            }
            return;
        }
        
        String qqIdStr = parts[1].trim();
        long targetQqId;
        
        try {
            targetQqId = Long.parseLong(qqIdStr);
        } catch (NumberFormatException e) {
            String message = "QQ号格式错误，必须是数字";
            if (qqMessageSender != null) {
                qqMessageSender.sendGroupMessage(groupId, message);
            } else if (messageSender != null) {
                messageSender.apply(groupId, message);
            }
            return;
        }
        
        if (targetQqId <= 0) {
            String message = "QQ号无效，必须大于0";
            if (qqMessageSender != null) {
                qqMessageSender.sendGroupMessage(groupId, message);
            } else if (messageSender != null) {
                messageSender.apply(groupId, message);
            }
            return;
        }
        
        // 检查是否已经在黑名单中
        if (banListManager.isBanned(targetQqId)) {
            String message = String.format("QQ号 %d 已在黑名单中", targetQqId);
            if (qqMessageSender != null) {
                qqMessageSender.sendGroupMessage(groupId, message);
            } else if (messageSender != null) {
                messageSender.apply(groupId, message);
            }
            return;
        }
        
        // 添加黑名单
        boolean success = banListManager.addBan(targetQqId);
        
        if (success) {
            String message = String.format("已成功拉黑QQ号：%d", targetQqId);
            if (qqMessageSender != null) {
                qqMessageSender.sendGroupMessage(groupId, message);
            } else if (messageSender != null) {
                messageSender.apply(groupId, message);
            }
            logger.info("管理员 {} 成功拉黑用户 {}", userId, targetQqId);
        } else {
            String message = String.format("拉黑失败，QQ号 %d 可能已在黑名单中", targetQqId);
            if (qqMessageSender != null) {
                qqMessageSender.sendGroupMessage(groupId, message);
            } else if (messageSender != null) {
                messageSender.apply(groupId, message);
            }
            logger.warn("管理员 {} 拉黑用户 {} 失败", userId, targetQqId);
        }
    }
    
    /**
     * 处理黑名单管理命令（KOOK消息）
     * @param channelId 频道ID
     * @param userId 用户ID（long，从字符串转换而来）
     * @param messageText 消息内容
     */
    public void handleBanCommandKook(String channelId, long userId, String messageText) {
        if (messageText == null || kookMessageSender == null) {
            return;
        }
        
        // 检查管理员权限
        if (!isAdmin(userId)) {
            logger.warn("非管理员尝试使用黑名单管理命令（KOOK），用户: {}", userId);
            kookMessageSender.sendChannelMessage(channelId, "权限不足");
            return;
        }
        
        String trimmed = messageText.trim();
        
        // 处理"黑名单列表"
        if (trimmed.equals(BAN_LIST_TRIGGER)) {
            handleBanListKook(channelId, userId);
            return;
        }
        
        // 处理"黑名单删除所有封禁用户投稿"
        if (trimmed.equals(BAN_DELETE_TIPS_TRIGGER)) {
            handleDeleteBannedUserTipsKook(channelId, userId);
            return;
        }
        
        // 处理"/ban (QQ号)"
        if (trimmed.startsWith(BAN_ADD_PREFIX)) {
            handleAddBanKook(channelId, userId, trimmed);
            return;
        }
    }
    
    /**
     * 处理"黑名单列表"命令（KOOK）
     */
    private void handleBanListKook(String channelId, long userId) {
        logger.info("管理员 {} 请求查看黑名单列表（KOOK）", userId);
        
        Set<Long> bannedUsers = banListManager.getAllBannedUsers();
        
        if (bannedUsers.isEmpty()) {
            kookMessageSender.sendChannelMessage(channelId, "当前黑名单为空");
            return;
        }
        
        StringBuilder result = new StringBuilder();
        result.append("黑名单列表（共").append(bannedUsers.size()).append("人）：\n");
        
        int index = 1;
        for (Long qqId : bannedUsers) {
            result.append(index).append(". ").append(qqId);
            if (index < bannedUsers.size()) {
                result.append("\n");
            }
            index++;
        }
        
        kookMessageSender.sendChannelMessage(channelId, result.toString());
        logger.info("管理员 {} 查看黑名单列表完成（KOOK），共 {} 人", userId, bannedUsers.size());
    }
    
    /**
     * 处理"黑名单删除所有封禁用户投稿"命令（KOOK）
     */
    private void handleDeleteBannedUserTipsKook(String channelId, long userId) {
        logger.info("管理员 {} 请求删除所有黑名单用户的投稿（KOOK）", userId);
        
        Set<Long> bannedUsers = banListManager.getAllBannedUsers();
        
        if (bannedUsers.isEmpty()) {
            kookMessageSender.sendChannelMessage(channelId, "当前黑名单为空，无需删除");
            return;
        }
        
        kookMessageSender.sendChannelMessage(channelId, "正在删除黑名单用户的投稿，请稍候...");
        
        int deletedCount = databaseManager.deleteTipsByQqIds(bannedUsers);
        
        if (deletedCount < 0) {
            kookMessageSender.sendChannelMessage(channelId, "删除投稿时发生错误，请查看日志");
            logger.error("管理员 {} 删除黑名单用户投稿时发生错误（KOOK）", userId);
            return;
        }
        
        String result = String.format("删除完成！共删除 %d 条投稿（涉及 %d 个黑名单用户）", 
                                     deletedCount, bannedUsers.size());
        kookMessageSender.sendChannelMessage(channelId, result);
        logger.info("管理员 {} 删除黑名单用户投稿完成（KOOK），共删除 {} 条投稿", userId, deletedCount);
    }
    
    /**
     * 处理"/ban (QQ号)"命令（KOOK）
     * @param command 命令字符串，格式：/ban (QQ号)
     */
    private void handleAddBanKook(String channelId, long userId, String command) {
        logger.info("管理员 {} 请求拉黑用户（KOOK），命令: {}", userId, command);
        
        // 解析命令：/ban (QQ号)
        String[] parts = command.split("\\s+");
        if (parts.length < 2) {
            kookMessageSender.sendChannelMessage(channelId, "命令格式错误，正确格式：/ban (QQ号)");
            return;
        }
        
        String qqIdStr = parts[1].trim();
        long targetQqId;
        
        try {
            targetQqId = Long.parseLong(qqIdStr);
        } catch (NumberFormatException e) {
            kookMessageSender.sendChannelMessage(channelId, "QQ号格式错误，必须是数字");
            return;
        }
        
        if (targetQqId <= 0) {
            kookMessageSender.sendChannelMessage(channelId, "QQ号无效，必须大于0");
            return;
        }
        
        // 检查是否已经在黑名单中
        if (banListManager.isBanned(targetQqId)) {
            kookMessageSender.sendChannelMessage(channelId, String.format("QQ号 %d 已在黑名单中", targetQqId));
            return;
        }
        
        // 添加黑名单
        boolean success = banListManager.addBan(targetQqId);
        
        if (success) {
            kookMessageSender.sendChannelMessage(channelId, String.format("已成功拉黑QQ号：%d", targetQqId));
            logger.info("管理员 {} 成功拉黑用户 {}（KOOK）", userId, targetQqId);
        } else {
            kookMessageSender.sendChannelMessage(channelId, String.format("拉黑失败，QQ号 %d 可能已在黑名单中", targetQqId));
            logger.warn("管理员 {} 拉黑用户 {} 失败（KOOK）", userId, targetQqId);
        }
    }
}
