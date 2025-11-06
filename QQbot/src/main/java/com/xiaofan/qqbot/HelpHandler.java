package com.xiaofan.qqbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiFunction;

/**
 * 帮助处理器
 * 检测"帮助"关键词，显示所有可用命令
 */
public class HelpHandler {
    private static final Logger logger = LoggerFactory.getLogger(HelpHandler.class);
    
    private static final String TRIGGER_KEYWORD = "帮助";
    
    private final BiFunction<Long, String, Boolean> messageSender;
    
    /**
     * 构造函数
     * @param messageSender 消息发送函数，接收群号和消息内容，返回是否发送成功
     */
    public HelpHandler(BiFunction<Long, String, Boolean> messageSender) {
        this.messageSender = messageSender;
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
     * 处理帮助请求
     * @param groupId 群号
     * @param userId QQ号
     * @param messageText 消息内容
     */
    public void handleHelp(long groupId, long userId, String messageText) {
        if (!shouldHandle(messageText)) {
            return;
        }
        
        logger.info("检测到帮助请求，群号: {}, QQ号: {}", groupId, userId);
        
        // 构建帮助菜单
        StringBuilder helpMessage = new StringBuilder();
        helpMessage.append("xiaofanbot帮助菜单\n\n");
        helpMessage.append("可用命令：\n\n");
        helpMessage.append("1. oi：基础回复（回复io）\n");
        helpMessage.append("2. 人数查询：查询Minecraft服务器在线人数\n");
        helpMessage.append("3. 签到：每日签到获得积分（24小时冷却）\n");
        helpMessage.append("4. 查询积分：查询当前签到积分\n");
        helpMessage.append("5. 投稿 （内容）：投稿内容到数据库（投稿和内容之间必须有空格）\n");
        helpMessage.append("6. tip：随机获取一条投稿内容\n");
        helpMessage.append("7. /c （内容）：发送消息到Minecraft服务器（仅限指定群组）\n");
        helpMessage.append("8. 帮助：显示此帮助菜单");
        
        messageSender.apply(groupId, helpMessage.toString());
        logger.info("帮助菜单已发送，群号: {}, QQ号: {}", groupId, userId);
    }
}

