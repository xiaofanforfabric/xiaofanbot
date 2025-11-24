package com.xiaofan.qqbot.handler;

import com.xiaofan.qqbot.manager.DatabaseManager;
import com.xiaofan.qqbot.send.KookMessageSender;
import com.xiaofan.qqbot.send.QQMessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 积分查询处理器
 * 检测"查询积分"关键词，查询用户当前积分
 */
public class PointsQueryHandler {
    private static final Logger logger = LoggerFactory.getLogger(PointsQueryHandler.class);
    
    private static final String TRIGGER_KEYWORD = "查询积分";
    
    private final DatabaseManager databaseManager;
    private final QQMessageSender qqMessageSender;
    private final KookMessageSender kookMessageSender;
    
    /**
     * 构造函数
     * @param qqMessageSender QQ消息发送器
     * @param kookMessageSender KOOK消息发送器
     */
    public PointsQueryHandler(QQMessageSender qqMessageSender, KookMessageSender kookMessageSender) {
        this.qqMessageSender = qqMessageSender;
        this.kookMessageSender = kookMessageSender;
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
            qqMessageSender.sendGroupMessage(groupId, "查询失败：无法获取QQ号");
            return;
        }
        
        logger.info("检测到积分查询请求，群号: {}, QQ号: {}", groupId, userId);
        
        try {
            // 检查用户是否存在
            boolean exists = databaseManager.userExists(userId);
            
            if (!exists) {
                // 用户不存在
                String message = "未注册用户，请先发送\"签到\"注册";
                qqMessageSender.sendGroupMessage(groupId, message);
                logger.info("用户未注册，QQ号: {}", userId);
            } else {
                // 用户存在，查询积分
                DatabaseManager.UserCheckInInfo userInfo = databaseManager.getUserInfo(userId);
                
                if (userInfo == null) {
                    qqMessageSender.sendGroupMessage(groupId, "查询失败：获取用户信息失败，请稍后重试");
                    logger.error("获取用户信息失败，QQ号: {}", userId);
                    return;
                }
                
                String message = String.format("查询成功，当前积分：%d", userInfo.qd);
                qqMessageSender.sendGroupMessage(groupId, message);
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
            
            qqMessageSender.sendGroupMessage(groupId, errorMessage);
        }
    }
    
    /**
     * 处理积分查询请求（KOOK消息）
     * @param channelId 频道ID（字符串）
     * @param userId 用户ID（字符串，需要转换为long用于数据库查询）
     * @param messageText 消息内容
     */
    public void handleQueryKook(String channelId, String userId, String messageText) {
        if (!shouldHandle(messageText)) {
            return;
        }
        
        // 尝试将KOOK用户ID转换为long（用于数据库查询）
        long userIdLong = 0;
        try {
            userIdLong = Long.parseLong(userId);
        } catch (NumberFormatException e) {
            logger.warn("无效的KOOK用户ID（无法转换为long）: {}", userId);
            kookMessageSender.sendChannelMessage(channelId, "查询失败：无法获取用户ID");
            return;
        }
        
        if (userIdLong <= 0) {
            logger.warn("无效的用户ID: {}", userIdLong);
            kookMessageSender.sendChannelMessage(channelId, "查询失败：无法获取用户ID");
            return;
        }
        
        logger.info("检测到积分查询请求（KOOK），频道: {}, 用户ID: {}", channelId, userId);
        
        try {
            // 检查用户是否存在
            boolean exists = databaseManager.userExists(userIdLong);
            
            if (!exists) {
                // 用户不存在
                String message = "未注册用户，请先发送\"签到\"注册";
                kookMessageSender.sendChannelMessage(channelId, message);
                logger.info("用户未注册（KOOK），用户ID: {}", userId);
            } else {
                // 用户存在，查询积分
                DatabaseManager.UserCheckInInfo userInfo = databaseManager.getUserInfo(userIdLong);
                
                if (userInfo == null) {
                    kookMessageSender.sendChannelMessage(channelId, "查询失败：获取用户信息失败，请稍后重试");
                    logger.error("获取用户信息失败（KOOK），用户ID: {}", userId);
                    return;
                }
                
                String message = String.format("查询成功，当前积分：%d", userInfo.qd);
                kookMessageSender.sendChannelMessage(channelId, message);
                logger.info("积分查询成功（KOOK），用户ID: {}, 积分: {}", userId, userInfo.qd);
            }
            
        } catch (Exception e) {
            logger.error("处理积分查询时发生错误（KOOK），用户ID: {}", userId, e);
            
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
            
            kookMessageSender.sendChannelMessage(channelId, errorMessage);
        }
    }
}
