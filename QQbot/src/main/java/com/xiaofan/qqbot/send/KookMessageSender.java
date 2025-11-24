package com.xiaofan.qqbot.send;

import com.xiaofan.qqbot.websocket.KookReplyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiFunction;

/**
 * KOOK消息发送器
 * 用于发送消息到KOOK频道
 */
public class KookMessageSender implements BiFunction<String, String, Boolean> {
    private static final Logger logger = LoggerFactory.getLogger(KookMessageSender.class);
    
    private final KookReplyServer kookReplyServer;
    
    public KookMessageSender(KookReplyServer kookReplyServer) {
        this.kookReplyServer = kookReplyServer;
    }
    
    /**
     * 发送频道消息
     * @param channelId 频道ID（字符串）
     * @param message 消息内容
     * @return 是否发送成功
     */
    @Override
    public Boolean apply(String channelId, String message) {
        return sendChannelMessage(channelId, message);
    }
    
    /**
     * 发送频道消息
     * @param channelId 频道ID（字符串）
     * @param message 消息内容
     * @return 是否发送成功
     */
    public boolean sendChannelMessage(String channelId, String message) {
        if (kookReplyServer == null) {
            logger.error("KOOK回复服务器未初始化，无法发送消息");
            return false;
        }
        
        return kookReplyServer.sendReply(channelId, message);
    }
}

