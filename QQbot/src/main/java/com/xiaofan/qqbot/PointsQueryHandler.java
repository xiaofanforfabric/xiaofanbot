package com.xiaofan.qqbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiFunction;

/**
 * 积分查询处理器
 * 检测"查询积分"关键词，查询用户当前积分
 */
public class PointsQueryHandler {
    private static final Logger logger = LoggerFactory.getLogger(PointsQueryHandler.class);
    
    private static final String TRIGGER_KEYWORD = "查询积分";
    
    private final DatabaseManager databaseManager;
    private final BiFunction<Long, String, Boolean> messageSender;
    
    /**
     * 构造函数
     * @param messageSender 消息发送函数，接收群号和消息内容，返回是否发送成功
     */
    public PointsQueryHandler(BiFunction<Long, String, Boolean> messageSender) {
        this.messageSender = messageSender;
        this.databaseManager = new DatabaseManager();
    }
    
    /**
     * 检查消息是否完全匹配触发关键词（去除首尾空格后精确匹配）
     */
    public boolean shouldHandle(String messageText) {
        if (messageText == null) {
            return false;
        }
        return messageText.trim().equals(TRIGGER_KEYWORD);
    }
    
    /**
     * 处理积分查询请求
     * @param groupId 群号
     * @param userId QQ号
     * @param messageText 消息内容
     */
    public void handleQuery(long groupId, long userId, String messageText) {
        if (!shouldHandle(messageText)) {
            return;
        }
        
        if (userId <= 0) {
            logger.warn("无效的QQ号: {}", userId);
            messageSender.apply(groupId, "查询失败：无法获取QQ号");
            return;
        }
        
        logger.info("检测到积分查询请求，群号: {}, QQ号: {}", groupId, userId);
        
        try {
            // 检查用户是否存在
            boolean exists = databaseManager.userExists(userId);
            
            if (!exists) {
                // 用户不存在
                String message = "未注册用户，请先发送\"签到\"注册";
                messageSender.apply(groupId, message);
                logger.info("用户未注册，QQ号: {}", userId);
            } else {
                // 用户存在，查询积分
                DatabaseManager.UserCheckInInfo userInfo = databaseManager.getUserInfo(userId);
                
                if (userInfo == null) {
                    messageSender.apply(groupId, "查询失败：获取用户信息失败，请稍后重试");
                    logger.error("获取用户信息失败，QQ号: {}", userId);
                    return;
                }
                
                String message = String.format("查询成功，当前积分：%d", userInfo.qd);
                messageSender.apply(groupId, message);
                logger.info("积分查询成功，QQ号: {}, 积分: {}", userId, userInfo.qd);
            }
            
        } catch (Exception e) {
            logger.error("处理积分查询时发生错误，QQ号: {}", userId, e);
            
            // 提供更友好的错误信息
            String errorMessage = "查询失败";
            if (e.getCause() != null && e.getCause().getMessage() != null) {
                String causeMsg = e.getCause().getMessage();
                if (causeMsg.contains("连接被拒绝") || causeMsg.contains("Communications link failure")) {
                    errorMessage = "查询失败：数据库连接失败，请检查数据库服务是否正常运行";
                } else {
                    errorMessage = "查询失败：" + causeMsg;
                }
            } else if (e.getMessage() != null) {
                errorMessage = "查询失败：" + e.getMessage();
            }
            
            messageSender.apply(groupId, errorMessage);
        }
    }
}

