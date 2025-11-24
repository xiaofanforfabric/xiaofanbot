package com.xiaofan.qqbot.handler;

import com.xiaofan.qqbot.manager.DatabaseManager;
import com.xiaofan.qqbot.manager.MuteListManager;
import com.xiaofan.qqbot.send.KookMessageSender;
import com.xiaofan.qqbot.send.QQMessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiFunction;

/**
 * 屏蔽处理器
 * 处理"/mute"命令，将用户的game_id添加到屏蔽列表
 */
public class MuteHandler {
    private static final Logger logger = LoggerFactory.getLogger(MuteHandler.class);
    
    private static final String TRIGGER_KEYWORD = "/mute";
    
    private final DatabaseManager databaseManager;
    private final MuteListManager muteListManager;
    private final BiFunction<Long, String, Boolean> messageSender;
    private final QQMessageSender qqMessageSender;
    private final KookMessageSender kookMessageSender;
    
    public MuteHandler(BiFunction<Long, String, Boolean> messageSender,
                      MuteListManager muteListManager) {
        this.messageSender = messageSender;
        this.qqMessageSender = null;
        this.kookMessageSender = null;
        this.databaseManager = new DatabaseManager();
        this.muteListManager = muteListManager;
    }
    
    public MuteHandler(QQMessageSender qqMessageSender,
                      KookMessageSender kookMessageSender,
                      MuteListManager muteListManager) {
        this.qqMessageSender = qqMessageSender;
        this.kookMessageSender = kookMessageSender;
        this.messageSender = null;
        this.databaseManager = new DatabaseManager();
        this.muteListManager = muteListManager;
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
     * 处理屏蔽请求（QQ消息）
     * @param groupId 群号
     * @param userId QQ号
     * @param messageText 消息内容
     */
    public void handleMute(long groupId, long userId, String messageText) {
        if (!shouldHandle(messageText)) {
            return;
        }
        
        if (userId <= 0) {
            logger.warn("无效的QQ号: {}", userId);
            if (qqMessageSender != null) {
                qqMessageSender.sendGroupMessage(groupId, "屏蔽失败：无法获取QQ号");
            } else if (messageSender != null) {
                messageSender.apply(groupId, "屏蔽失败：无法获取QQ号");
            }
            return;
        }
        
        logger.info("检测到屏蔽请求，群号: {}, QQ号: {}", groupId, userId);
        
        try {
            // 检查用户是否存在
            boolean exists = databaseManager.userExists(userId);
            
            if (!exists) {
                String message = "你还没有绑定游戏账号，请先绑定游戏账号";
                if (qqMessageSender != null) {
                    qqMessageSender.sendGroupMessage(groupId, message);
                } else if (messageSender != null) {
                    messageSender.apply(groupId, message);
                }
                logger.info("屏蔽失败：用户未注册，QQ号: {}", userId);
                return;
            }
            
            // 获取用户的game_id
            String gameId = databaseManager.getGameId(userId);
            
            if (gameId == null || gameId.trim().isEmpty()) {
                String message = "你还没有绑定游戏账号，请先绑定";
                if (qqMessageSender != null) {
                    qqMessageSender.sendGroupMessage(groupId, message);
                } else if (messageSender != null) {
                    messageSender.apply(groupId, message);
                }
                logger.info("屏蔽失败：用户未绑定游戏ID，QQ号: {}", userId);
                return;
            }
            
            // 添加到屏蔽列表
            boolean success = muteListManager.addMutedGameId(gameId);
            
            if (success) {
                String message = "屏蔽成功";
                if (qqMessageSender != null) {
                    qqMessageSender.sendGroupMessage(groupId, message);
                } else if (messageSender != null) {
                    messageSender.apply(groupId, message);
                }
                logger.info("屏蔽成功，QQ号: {}, game_id: {}", userId, gameId);
            } else {
                String message = "屏蔽失败：写入文件失败，请稍后重试";
                if (qqMessageSender != null) {
                    qqMessageSender.sendGroupMessage(groupId, message);
                } else if (messageSender != null) {
                    messageSender.apply(groupId, message);
                }
                logger.error("屏蔽失败：写入文件失败，QQ号: {}, game_id: {}", userId, gameId);
            }
            
        } catch (Exception e) {
            logger.error("处理屏蔽时发生错误，QQ号: {}", userId, e);
            String message = "屏蔽失败：" + e.getMessage();
            if (qqMessageSender != null) {
                qqMessageSender.sendGroupMessage(groupId, message);
            } else if (messageSender != null) {
                messageSender.apply(groupId, message);
            }
        }
    }
    
    /**
     * 处理屏蔽请求（KOOK消息）
     * @param channelId 频道ID
     * @param userId 用户ID（字符串）
     * @param messageText 消息内容
     */
    public void handleMuteKook(String channelId, String userId, String messageText) {
        if (!shouldHandle(messageText)) {
            return;
        }
        
        // 将KOOK的userId转换为long
        long userIdLong = 0;
        try {
            userIdLong = Long.parseLong(userId);
        } catch (NumberFormatException e) {
            logger.error("无法将KOOK用户ID转换为long: {}", userId);
            if (kookMessageSender != null) {
                kookMessageSender.sendChannelMessage(channelId, "屏蔽失败：无效的用户ID");
            }
            return;
        }
        
        if (userIdLong <= 0) {
            logger.warn("无效的用户ID: {}", userId);
            if (kookMessageSender != null) {
                kookMessageSender.sendChannelMessage(channelId, "屏蔽失败：无法获取用户ID");
            }
            return;
        }
        
        logger.info("检测到屏蔽请求（KOOK），频道: {}, 用户: {}", channelId, userId);
        
        try {
            // 检查用户是否存在
            boolean exists = databaseManager.userExists(userIdLong);
            
            if (!exists) {
                if (kookMessageSender != null) {
                    kookMessageSender.sendChannelMessage(channelId, "你还没有绑定游戏账号，请先绑定游戏账号");
                }
                logger.info("屏蔽失败：用户未注册，用户ID: {}", userId);
                return;
            }
            
            // 获取用户的game_id
            String gameId = databaseManager.getGameId(userIdLong);
            
            if (gameId == null || gameId.trim().isEmpty()) {
                if (kookMessageSender != null) {
                    kookMessageSender.sendChannelMessage(channelId, "你还没有绑定游戏账号，请先绑定");
                }
                logger.info("屏蔽失败：用户未绑定游戏ID，用户ID: {}", userId);
                return;
            }
            
            // 添加到屏蔽列表
            boolean success = muteListManager.addMutedGameId(gameId);
            
            if (success) {
                if (kookMessageSender != null) {
                    kookMessageSender.sendChannelMessage(channelId, "屏蔽成功");
                }
                logger.info("屏蔽成功，用户ID: {}, game_id: {}", userId, gameId);
            } else {
                if (kookMessageSender != null) {
                    kookMessageSender.sendChannelMessage(channelId, "屏蔽失败：写入文件失败，请稍后重试");
                }
                logger.error("屏蔽失败：写入文件失败，用户ID: {}, game_id: {}", userId, gameId);
            }
            
        } catch (Exception e) {
            logger.error("处理屏蔽时发生错误，用户ID: {}", userId, e);
            if (kookMessageSender != null) {
                kookMessageSender.sendChannelMessage(channelId, "屏蔽失败：" + e.getMessage());
            }
        }
    }
}
