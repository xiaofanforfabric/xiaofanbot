package com.xiaofan.qqbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.function.BiFunction;

/**
 * Tip处理器
 * 检测"tip"关键词，随机返回一条投稿内容
 */
public class TipHandler {
    private static final Logger logger = LoggerFactory.getLogger(TipHandler.class);
    
    private static final String TRIGGER_KEYWORD = "tip";
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final DatabaseManager databaseManager;
    private final BiFunction<Long, String, Boolean> messageSender;
    
    /**
     * 构造函数
     * @param messageSender 消息发送函数，接收群号和消息内容，返回是否发送成功
     */
    public TipHandler(BiFunction<Long, String, Boolean> messageSender) {
        this.messageSender = messageSender;
        this.databaseManager = new DatabaseManager();
    }
    
    /**
     * 检查消息是否完全匹配触发关键词（去除首尾空格后精确匹配，不区分大小写）
     */
    public boolean shouldHandle(String messageText) {
        if (messageText == null) {
            return false;
        }
        return messageText.trim().equalsIgnoreCase(TRIGGER_KEYWORD);
    }
    
    /**
     * 处理tip请求
     * @param groupId 群号
     * @param userId QQ号
     * @param messageText 消息内容
     */
    public void handleTip(long groupId, long userId, String messageText) {
        if (!shouldHandle(messageText)) {
            return;
        }
        
        logger.info("检测到tip请求，群号: {}, QQ号: {}", groupId, userId);
        
        try {
            // 随机获取一条tip
            DatabaseManager.TipInfo tipInfo = databaseManager.getRandomTip();
            
            if (tipInfo == null) {
                messageSender.apply(groupId, "暂无投稿内容，请先发送「投稿 （内容）」进行投稿。");
                logger.info("tip查询失败：暂无数据");
                return;
            }
            
            // 格式化时间
            String regTimeStr = tipInfo.regTime != null 
                ? tipInfo.regTime.format(DATETIME_FORMATTER) 
                : "未知时间";
            
            // 构建回复消息
            StringBuilder message = new StringBuilder();
            message.append("————Tip：#").append(tipInfo.id).append("\n");
            message.append(tipInfo.tip).append("\n");
            message.append("————由").append(tipInfo.regUser).append("在").append(regTimeStr).append("投稿————");
            
            messageSender.apply(groupId, message.toString());
            logger.info("tip查询成功，ID: {}, 用户: {}", tipInfo.id, tipInfo.regUser);
            
        } catch (Exception e) {
            logger.error("处理tip时发生错误，QQ号: {}", userId, e);
            
            // 提供更友好的错误信息
            String errorMessage = "tip查询失败";
            if (e.getCause() != null && e.getCause().getMessage() != null) {
                String causeMsg = e.getCause().getMessage();
                if (causeMsg.contains("连接被拒绝") || causeMsg.contains("Communications link failure")) {
                    errorMessage = "tip查询失败：数据库连接失败，请检查数据库服务是否正常运行";
                } else {
                    errorMessage = "tip查询失败：" + causeMsg;
                }
            } else if (e.getMessage() != null) {
                errorMessage = "tip查询失败：" + e.getMessage();
            }
            
            messageSender.apply(groupId, errorMessage);
        }
    }
}

