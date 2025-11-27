package com.xiaofan.qqbot.handler;

import com.xiaofan.qqbot.manager.DatabaseManager;
import com.xiaofan.qqbot.send.KookMessageSender;
import com.xiaofan.qqbot.send.QQMessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 投稿处理器
 * 检测"投稿"关键词，处理用户投稿逻辑
 * 格式：投稿 （内容）
 */
public class TipSubmissionHandler {
    private static final Logger logger = LoggerFactory.getLogger(TipSubmissionHandler.class);
    
    private static final String TRIGGER_KEYWORD = "投稿";
    
    private final DatabaseManager databaseManager;
    private final QQMessageSender qqMessageSender;
    private final KookMessageSender kookMessageSender;
    
    /**
     * 构造函数
     * @param qqMessageSender QQ消息发送器
     * @param kookMessageSender KOOK消息发送器
     */
    public TipSubmissionHandler(QQMessageSender qqMessageSender, KookMessageSender kookMessageSender) {
        this.qqMessageSender = qqMessageSender;
        this.kookMessageSender = kookMessageSender;
        this.databaseManager = new DatabaseManager();
    }
    
    /**
     * 检查消息是否以"投稿"开头（去除首尾空格后）
     */
    public boolean shouldHandle(String messageText) {
        if (messageText == null) {
            return false;
        }
        String trimmed = messageText.trim();
        return trimmed.startsWith(TRIGGER_KEYWORD);
    }
    
    /**
     * 提取投稿内容
     * 格式：投稿 （内容）- "投稿"和内容之间必须有一个空格
     * @param messageText 原始消息
     * @return 投稿内容，如果格式不正确返回null
     */
    private String extractTipContent(String messageText) {
        if (messageText == null) {
            return null;
        }
        
        String trimmed = messageText.trim();
        
        // 检查是否以"投稿"开头
        if (!trimmed.startsWith(TRIGGER_KEYWORD)) {
            return null;
        }
        
        // 检查"投稿"后面是否紧跟一个空格
        if (trimmed.length() <= TRIGGER_KEYWORD.length()) {
            return null; // "投稿"后面没有内容
        }
        
        // 检查"投稿"后面的第一个字符是否是空格
        char nextChar = trimmed.charAt(TRIGGER_KEYWORD.length());
        if (nextChar != ' ') {
            return null; // "投稿"后面不是空格，格式不正确
        }
        
        // 提取"投稿 "后面的内容（跳过空格）
        String content = trimmed.substring(TRIGGER_KEYWORD.length() + 1).trim();
        
        // 检查是否有内容
        if (content.isEmpty()) {
            return null;
        }
        
        return content;
    }
    
    /**
     * 处理投稿请求
     * @param groupId 群号
     * @param userId QQ号
     * @param messageText 消息内容
     */
    public void handleSubmission(long groupId, long userId, String messageText) {
        if (!shouldHandle(messageText)) {
            return;
        }
        
        if (userId <= 0) {
            logger.warn("无效的QQ号: {}", userId);
            qqMessageSender.sendGroupMessage(groupId, "投稿失败：无法获取QQ号");
            return;
        }
        
        logger.info("检测到投稿请求，群号: {}, QQ号: {}", groupId, userId);
        
        try {
            // 提取投稿内容
            String tipContent = extractTipContent(messageText);
            
            if (tipContent == null || tipContent.isEmpty()) {
                qqMessageSender.sendGroupMessage(groupId, "投稿失败：请使用格式「投稿（内容）」，内容不能为空");
                logger.warn("投稿内容为空，QQ号: {}", userId);
                return;
            }
            
            // 插入数据库
            String regUser = String.valueOf(userId);
            boolean success = databaseManager.insertTip(tipContent, regUser);
            
            if (success) {
                qqMessageSender.sendGroupMessage(groupId, "投稿成功！感谢您的投稿！");
                logger.info("投稿成功，QQ号: {}, 内容长度: {}", userId, tipContent.length());
            } else {
                qqMessageSender.sendGroupMessage(groupId, "投稿失败：数据库操作失败，请稍后重试");
                logger.error("投稿失败，QQ号: {}", userId);
            }
            
        } catch (Exception e) {
            logger.error("处理投稿时发生错误，QQ号: {}", userId, e);
            
            // 提供更友好的错误信息
            String errorMessage = "投稿失败";
            if (e.getCause() != null && e.getCause().getMessage() != null) {
                String causeMsg = e.getCause().getMessage();
                if (causeMsg.contains("连接被拒绝") || causeMsg.contains("Communications link failure")) {
                    errorMessage = "投稿失败：数据库连接失败，请检查数据库服务是否正常运行";
                } else {
                    errorMessage = "投稿失败：" + causeMsg;
                }
            } else if (e.getMessage() != null) {
                errorMessage = "投稿失败：" + e.getMessage();
            }
            
            qqMessageSender.sendGroupMessage(groupId, errorMessage);
        }
    }
    
    /**
     * 处理投稿请求（KOOK消息）
     * @param channelId 频道ID（字符串）
     * @param userId 用户ID（字符串，需要转换为long用于数据库查询）
     * @param messageText 消息内容
     */
    public void handleSubmissionKook(String channelId, String userId, String messageText) {
        if (!shouldHandle(messageText)) {
            return;
        }
        
        // 尝试将KOOK用户ID转换为long（用于数据库查询）
        long userIdLong = 0;
        try {
            userIdLong = Long.parseLong(userId);
        } catch (NumberFormatException e) {
            logger.warn("无效的KOOK用户ID（无法转换为long）: {}", userId);
            kookMessageSender.sendChannelMessage(channelId, "投稿失败：无法获取用户ID");
            return;
        }
        
        if (userIdLong <= 0) {
            logger.warn("无效的用户ID: {}", userIdLong);
            kookMessageSender.sendChannelMessage(channelId, "投稿失败：无法获取用户ID");
            return;
        }
        
        logger.info("检测到投稿请求（KOOK），频道: {}, 用户ID: {}", channelId, userId);
        
        try {
            // 提取投稿内容
            String tipContent = extractTipContent(messageText);
            
            if (tipContent == null || tipContent.isEmpty()) {
                kookMessageSender.sendChannelMessage(channelId, "投稿失败：请使用格式「投稿（内容）」，内容不能为空");
                logger.warn("投稿内容为空（KOOK），用户ID: {}", userId);
                return;
            }
            
            // 插入数据库
            String regUser = String.valueOf(userIdLong);
            boolean success = databaseManager.insertTip(tipContent, regUser);
            
            if (success) {
                kookMessageSender.sendChannelMessage(channelId, "投稿成功！感谢您的投稿！");
                logger.info("投稿成功（KOOK），用户ID: {}, 内容长度: {}", userId, tipContent.length());
            } else {
                kookMessageSender.sendChannelMessage(channelId, "投稿失败：数据库操作失败，请稍后重试");
                logger.error("投稿失败（KOOK），用户ID: {}", userId);
            }
            
        } catch (Exception e) {
            logger.error("处理投稿时发生错误（KOOK），用户ID: {}", userId, e);
            
            // 提供更友好的错误信息
            String errorMessage = "投稿失败";
            if (e.getCause() != null && e.getCause().getMessage() != null) {
                String causeMsg = e.getCause().getMessage();
                if (causeMsg.contains("连接被拒绝") || causeMsg.contains("Communications link failure")) {
                    errorMessage = "投稿失败：数据库连接失败，请检查数据库服务是否正常运行";
                } else {
                    errorMessage = "投稿失败：" + causeMsg;
                }
            } else if (e.getMessage() != null) {
                errorMessage = "投稿失败：" + e.getMessage();
            }
            
            kookMessageSender.sendChannelMessage(channelId, errorMessage);
        }
    }
}
