package com.xiaofan.qqbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.BiFunction;

/**
 * 签到处理器
 * 检测"签到"关键词，处理用户签到逻辑
 */
public class CheckInHandler {
    private static final Logger logger = LoggerFactory.getLogger(CheckInHandler.class);
    
    private static final String TRIGGER_KEYWORD = "签到";
    private static final long CHECK_IN_INTERVAL_HOURS = 24; // 签到间隔24小时
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final DatabaseManager databaseManager;
    private final BiFunction<Long, String, Boolean> messageSender;
    
    /**
     * 构造函数
     * @param messageSender 消息发送函数，接收群号和消息内容，返回是否发送成功
     */
    public CheckInHandler(BiFunction<Long, String, Boolean> messageSender) {
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
     * 处理签到请求
     * @param groupId 群号
     * @param userId QQ号
     * @param messageText 消息内容
     */
    public void handleCheckIn(long groupId, long userId, String messageText) {
        if (!shouldHandle(messageText)) {
            return;
        }
        
        if (userId <= 0) {
            logger.warn("无效的QQ号: {}", userId);
            messageSender.apply(groupId, "签到失败：无法获取QQ号");
            return;
        }
        
        logger.info("检测到签到请求，群号: {}, QQ号: {}", groupId, userId);
        
        try {
            // 检查用户是否存在
            boolean exists = databaseManager.userExists(userId);
            
            if (!exists) {
                // 用户不存在，自动注册
                boolean registered = databaseManager.registerUser(userId);
                
                if (registered) {
                    // 获取注册后的用户信息
                    DatabaseManager.UserCheckInInfo userInfo = databaseManager.getUserInfo(userId);
                    
                    if (userInfo != null) {
                        String regTimeStr = userInfo.regTime != null 
                            ? userInfo.regTime.format(DATETIME_FORMATTER) 
                            : "未知";
                        
                        String message = String.format("首次签到成功，当前积分：%d，注册时间：%s", 
                            userInfo.qd, regTimeStr);
                        messageSender.apply(groupId, message);
                        logger.info("首次签到成功，QQ号: {}, 积分: {}", userId, userInfo.qd);
                    } else {
                        messageSender.apply(groupId, "首次签到成功，当前积分：1");
                    }
                } else {
                    messageSender.apply(groupId, "签到失败：用户注册失败，请稍后重试");
                    logger.error("用户注册失败，QQ号: {}", userId);
                }
            } else {
                // 用户存在，检查上次签到时间
                DatabaseManager.UserCheckInInfo userInfo = databaseManager.getUserInfo(userId);
                
                if (userInfo == null) {
                    messageSender.apply(groupId, "签到失败：获取用户信息失败，请稍后重试");
                    logger.error("获取用户信息失败，QQ号: {}", userId);
                    return;
                }
                
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime lastCheckIn = userInfo.qdLastTime;
                
                if (lastCheckIn == null) {
                    // 如果上次签到时间为空，允许签到并更新
                    boolean updated = databaseManager.updateCheckIn(userId);
                    if (updated) {
                        DatabaseManager.UserCheckInInfo updatedInfo = databaseManager.getUserInfo(userId);
                        int newQd = updatedInfo != null ? updatedInfo.qd : (userInfo.qd + 1);
                        String message = String.format("签到成功，当前积分：%d", newQd);
                        messageSender.apply(groupId, message);
                        logger.info("签到成功（上次签到时间为空），QQ号: {}, 新积分: {}", userId, newQd);
                    } else {
                        messageSender.apply(groupId, "签到失败：更新失败，请稍后重试");
                    }
                    return;
                }
                
                // 计算时间差
                Duration duration = Duration.between(lastCheckIn, now);
                long hours = duration.toHours();
                
                if (hours >= CHECK_IN_INTERVAL_HOURS) {
                    // 超过24小时，允许签到
                    boolean updated = databaseManager.updateCheckIn(userId);
                    if (updated) {
                        DatabaseManager.UserCheckInInfo updatedInfo = databaseManager.getUserInfo(userId);
                        int newQd = updatedInfo != null ? updatedInfo.qd : (userInfo.qd + 1);
                        String message = String.format("签到成功，当前积分：%d", newQd);
                        messageSender.apply(groupId, message);
                        logger.info("签到成功，QQ号: {}, 新积分: {}", userId, newQd);
                    } else {
                        messageSender.apply(groupId, "签到失败：更新失败，请稍后重试");
                    }
                } else {
                    // 未超过24小时，计算剩余时间
                    long remainingHours = CHECK_IN_INTERVAL_HOURS - hours;
                    long remainingMinutes = duration.toMinutes() % 60;
                    
                    String remainingTime;
                    if (remainingHours > 0) {
                        remainingTime = String.format("%d小时%d分钟", remainingHours, remainingMinutes);
                    } else {
                        remainingTime = String.format("%d分钟", remainingMinutes);
                    }
                    
                    String message = String.format("签到失败，与上一次签到未满24小时，还剩：%s", remainingTime);
                    messageSender.apply(groupId, message);
                    logger.info("签到失败（未满24小时），QQ号: {}, 剩余时间: {}", userId, remainingTime);
                }
            }
            
        } catch (Exception e) {
            logger.error("处理签到时发生错误，QQ号: {}", userId, e);
            
            // 提供更友好的错误信息
            String errorMessage = "签到失败";
            if (e.getCause() != null && e.getCause().getMessage() != null) {
                String causeMsg = e.getCause().getMessage();
                if (causeMsg.contains("连接被拒绝") || causeMsg.contains("Communications link failure")) {
                    errorMessage = "签到失败：数据库连接失败，请检查数据库服务是否正常运行";
                } else {
                    errorMessage = "签到失败：" + causeMsg;
                }
            } else if (e.getMessage() != null) {
                errorMessage = "签到失败：" + e.getMessage();
            }
            
            messageSender.apply(groupId, errorMessage);
        }
    }
}

