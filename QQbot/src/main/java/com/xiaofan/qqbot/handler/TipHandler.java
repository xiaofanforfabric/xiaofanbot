package com.xiaofan.qqbot.handler;

import com.xiaofan.qqbot.manager.DatabaseManager;
import com.xiaofan.qqbot.send.KookMessageSender;
import com.xiaofan.qqbot.send.QQMessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;

/**
 * Tip处理器
 * 检测"tip"关键词，随机返回一条投稿内容
 */
public class TipHandler {
    private static final Logger logger = LoggerFactory.getLogger(TipHandler.class);
    
    private static final String TRIGGER_KEYWORD = "tip";
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final DatabaseManager databaseManager;
    private final QQMessageSender qqMessageSender;
    private final KookMessageSender kookMessageSender;
    
    /**
     * 构造函数
     * @param qqMessageSender QQ消息发送器
     * @param kookMessageSender KOOK消息发送器
     */
    public TipHandler(QQMessageSender qqMessageSender, KookMessageSender kookMessageSender) {
        this.qqMessageSender = qqMessageSender;
        this.kookMessageSender = kookMessageSender;
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
                qqMessageSender.sendGroupMessage(groupId, "暂无投稿内容，请先发送「投稿（内容）」进行投稿");
                logger.info("tip查询失败：暂无数据");
                return;
            }
            
            // 格式化时间
            String regTimeStr = tipInfo.regTime != null 
                ? tipInfo.regTime.format(DATETIME_FORMATTER) 
                : "未知时间";
            
            // 构建回复消息
            StringBuilder message = new StringBuilder();
            message.append("————Tip ").append(tipInfo.id).append("\n");
            message.append(tipInfo.tip).append("\n");
            message.append("————由").append(tipInfo.regUser).append("于").append(regTimeStr).append("投稿————");
            
            qqMessageSender.sendGroupMessage(groupId, message.toString());
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
            
            qqMessageSender.sendGroupMessage(groupId, errorMessage);
        }
    }
    
    /**
     * 处理tip请求（KOOK消息）
     * @param channelId 频道ID（字符串）
     * @param userId 用户ID（字符串）
     * @param messageText 消息内容
     */
    public void handleTipKook(String channelId, String userId, String messageText) {
        if (!shouldHandle(messageText)) {
            return;
        }
        
        logger.info("检测到tip请求（KOOK），频道: {}, 用户ID: {}", channelId, userId);
        
        try {
            // 随机获取一条tip
            DatabaseManager.TipInfo tipInfo = databaseManager.getRandomTip();
            
            if (tipInfo == null) {
                kookMessageSender.sendChannelMessage(channelId, "暂无投稿内容，请先发送「投稿（内容）」进行投稿");
                logger.info("tip查询失败（KOOK）：暂无数据");
                return;
            }
            
            // 格式化时间
            String regTimeStr = tipInfo.regTime != null 
                ? tipInfo.regTime.format(DATETIME_FORMATTER) 
                : "未知时间";
            
            // 构建回复消息
            StringBuilder message = new StringBuilder();
            message.append("————Tip ").append(tipInfo.id).append("\n");
            message.append(tipInfo.tip).append("\n");
            message.append("————由").append(tipInfo.regUser).append("于").append(regTimeStr).append("投稿————");
            
            kookMessageSender.sendChannelMessage(channelId, message.toString());
            logger.info("tip查询成功（KOOK），ID: {}, 用户: {}", tipInfo.id, tipInfo.regUser);
            
        } catch (Exception e) {
            logger.error("处理tip时发生错误（KOOK），用户ID: {}", userId, e);
            
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
            
            kookMessageSender.sendChannelMessage(channelId, errorMessage);
        }
    }
}
