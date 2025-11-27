package com.xiaofan.qqbot.handler;

import com.xiaofan.qqbot.manager.DatabaseManager;
import com.xiaofan.qqbot.send.KookMessageSender;
import com.xiaofan.qqbot.send.QQMessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
    private final QQMessageSender qqMessageSender;
    private final KookMessageSender kookMessageSender;
    
    /**
     * 构造函数
     * @param qqMessageSender QQ消息发送器
     * @param kookMessageSender KOOK消息发送器
     */
    public CheckInHandler(QQMessageSender qqMessageSender, KookMessageSender kookMessageSender) {
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
            qqMessageSender.sendGroupMessage(groupId, "签到失败：无法获取QQ号");
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
                        qqMessageSender.sendGroupMessage(groupId, message);
                        logger.info("首次签到成功，QQ号: {}, 积分: {}", userId, userInfo.qd);
                    } else {
                        qqMessageSender.sendGroupMessage(groupId, "首次签到成功，当前积分：1");
                    }
                } else {
                    qqMessageSender.sendGroupMessage(groupId, "签到失败：用户注册失败，请稍后重试");
                    logger.error("用户注册失败，QQ号: {}", userId);
                }
            } else {
                // 用户存在，检查上次签到时间
                DatabaseManager.UserCheckInInfo userInfo = databaseManager.getUserInfo(userId);
                
                if (userInfo == null) {
                    qqMessageSender.sendGroupMessage(groupId, "签到失败：获取用户信息失败，请稍后重试");
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
                        qqMessageSender.sendGroupMessage(groupId, message);
                        logger.info("签到成功（上次签到时间为空），QQ号: {}, 新积分: {}", userId, newQd);
                    } else {
                        qqMessageSender.sendGroupMessage(groupId, "签到失败：更新失败，请稍后重试");
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
                        qqMessageSender.sendGroupMessage(groupId, message);
                        logger.info("签到成功，QQ号: {}, 新积分: {}", userId, newQd);
                    } else {
                        qqMessageSender.sendGroupMessage(groupId, "签到失败：更新失败，请稍后重试");
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
                    qqMessageSender.sendGroupMessage(groupId, message);
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
            
            qqMessageSender.sendGroupMessage(groupId, errorMessage);
        }
    }
    
    /**
     * 处理签到请求（KOOK消息）
     * @param channelId 频道ID（字符串）
     * @param userId 用户ID（字符串，需要转换为long用于数据库查询）
     * @param messageText 消息内容
     */
    public void handleCheckInKook(String channelId, String userId, String messageText) {
        if (!shouldHandle(messageText)) {
            return;
        }
        
        // 尝试将KOOK用户ID转换为long（用于数据库查询）
        long userIdLong = 0;
        try {
            userIdLong = Long.parseLong(userId);
        } catch (NumberFormatException e) {
            logger.warn("无效的KOOK用户ID（无法转换为long）: {}", userId);
            kookMessageSender.sendChannelMessage(channelId, "签到失败：无法获取用户ID");
            return;
        }
        
        if (userIdLong <= 0) {
            logger.warn("无效的用户ID: {}", userIdLong);
            kookMessageSender.sendChannelMessage(channelId, "签到失败：无法获取用户ID");
            return;
        }
        
        logger.info("检测到签到请求（KOOK），频道: {}, 用户ID: {}", channelId, userId);
        
        try {
            // 检查用户是否存在
            boolean exists = databaseManager.userExists(userIdLong);
            
            if (!exists) {
                // 用户不存在，自动注册
                boolean registered = databaseManager.registerUser(userIdLong);
                
                if (registered) {
                    // 获取注册后的用户信息
                    DatabaseManager.UserCheckInInfo userInfo = databaseManager.getUserInfo(userIdLong);
                    
                    if (userInfo != null) {
                        String regTimeStr = userInfo.regTime != null 
                            ? userInfo.regTime.format(DATETIME_FORMATTER) 
                            : "未知";
                        
                        String message = String.format("首次签到成功，当前积分：%d，注册时间：%s", 
                            userInfo.qd, regTimeStr);
                        kookMessageSender.sendChannelMessage(channelId, message);
                        logger.info("首次签到成功（KOOK），用户ID: {}, 积分: {}", userId, userInfo.qd);
                    } else {
                        kookMessageSender.sendChannelMessage(channelId, "首次签到成功，当前积分：1");
                    }
                } else {
                    kookMessageSender.sendChannelMessage(channelId, "签到失败：用户注册失败，请稍后重试");
                    logger.error("用户注册失败（KOOK），用户ID: {}", userId);
                }
            } else {
                // 用户存在，检查上次签到时间
                DatabaseManager.UserCheckInInfo userInfo = databaseManager.getUserInfo(userIdLong);
                
                if (userInfo == null) {
                    kookMessageSender.sendChannelMessage(channelId, "签到失败：获取用户信息失败，请稍后重试");
                    logger.error("获取用户信息失败（KOOK），用户ID: {}", userId);
                    return;
                }
                
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime lastCheckIn = userInfo.qdLastTime;
                
                if (lastCheckIn == null) {
                    // 如果上次签到时间为空，允许签到并更新
                    boolean updated = databaseManager.updateCheckIn(userIdLong);
                    if (updated) {
                        DatabaseManager.UserCheckInInfo updatedInfo = databaseManager.getUserInfo(userIdLong);
                        int newQd = updatedInfo != null ? updatedInfo.qd : (userInfo.qd + 1);
                        String message = String.format("签到成功，当前积分：%d", newQd);
                        kookMessageSender.sendChannelMessage(channelId, message);
                        logger.info("签到成功（上次签到时间为空，KOOK），用户ID: {}, 新积分: {}", userId, newQd);
                    } else {
                        kookMessageSender.sendChannelMessage(channelId, "签到失败：更新失败，请稍后重试");
                    }
                    return;
                }
                
                // 计算时间差
                Duration duration = Duration.between(lastCheckIn, now);
                long hours = duration.toHours();
                
                if (hours >= CHECK_IN_INTERVAL_HOURS) {
                    // 超过24小时，允许签到
                    boolean updated = databaseManager.updateCheckIn(userIdLong);
                    if (updated) {
                        DatabaseManager.UserCheckInInfo updatedInfo = databaseManager.getUserInfo(userIdLong);
                        int newQd = updatedInfo != null ? updatedInfo.qd : (userInfo.qd + 1);
                        String message = String.format("签到成功，当前积分：%d", newQd);
                        kookMessageSender.sendChannelMessage(channelId, message);
                        logger.info("签到成功（KOOK），用户ID: {}, 新积分: {}", userId, newQd);
                    } else {
                        kookMessageSender.sendChannelMessage(channelId, "签到失败：更新失败，请稍后重试");
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
                    kookMessageSender.sendChannelMessage(channelId, message);
                    logger.info("签到失败（未满24小时，KOOK），用户ID: {}, 剩余时间: {}", userId, remainingTime);
                }
            }
            
        } catch (Exception e) {
            logger.error("处理签到时发生错误（KOOK），用户ID: {}", userId, e);
            
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
            
            kookMessageSender.sendChannelMessage(channelId, errorMessage);
        }
    }
}
