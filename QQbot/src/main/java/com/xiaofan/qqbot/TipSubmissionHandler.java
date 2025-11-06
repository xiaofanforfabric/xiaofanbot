package com.xiaofan.qqbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiFunction;

/**
 * 投稿处理器
 * 检测"投稿"关键词，处理用户投稿逻辑
 * 格式：投稿 （内容）
 */
public class TipSubmissionHandler {
    private static final Logger logger = LoggerFactory.getLogger(TipSubmissionHandler.class);
    
    private static final String TRIGGER_KEYWORD = "投稿";
    
    private final DatabaseManager databaseManager;
    private final BiFunction<Long, String, Boolean> messageSender;
    
    /**
     * 构造函数
     * @param messageSender 消息发送函数，接收群号和消息内容，返回是否发送成功
     */
    public TipSubmissionHandler(BiFunction<Long, String, Boolean> messageSender) {
        this.messageSender = messageSender;
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
            messageSender.apply(groupId, "投稿失败：无法获取QQ号");
            return;
        }
        
        logger.info("检测到投稿请求，群号: {}, QQ号: {}", groupId, userId);
        
        try {
            // 提取投稿内容
            String tipContent = extractTipContent(messageText);
            
            if (tipContent == null || tipContent.isEmpty()) {
                messageSender.apply(groupId, "投稿失败：请使用格式「投稿 （内容）」，内容不能为空");
                logger.warn("投稿内容为空，QQ号: {}", userId);
                return;
            }
            
            // 插入数据库
            String regUser = String.valueOf(userId);
            boolean success = databaseManager.insertTip(tipContent, regUser);
            
            if (success) {
                messageSender.apply(groupId, "投稿成功！感谢您的投稿。");
                logger.info("投稿成功，QQ号: {}, 内容长度: {}", userId, tipContent.length());
            } else {
                messageSender.apply(groupId, "投稿失败：数据库操作失败，请稍后重试");
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
            
            messageSender.apply(groupId, errorMessage);
        }
    }
}

