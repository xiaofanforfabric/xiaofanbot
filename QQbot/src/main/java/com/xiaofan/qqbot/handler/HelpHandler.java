package com.xiaofan.qqbot.handler;

import com.xiaofan.qqbot.send.KookMessageSender;
import com.xiaofan.qqbot.send.QQMessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 帮助处理器
 * 检测"帮助"关键词，显示所有可用命令
 */
public class HelpHandler {
    private static final Logger logger = LoggerFactory.getLogger(HelpHandler.class);
    
    private static final String TRIGGER_KEYWORD = "帮助";
    
    private final QQMessageSender qqMessageSender; // QQ消息发送器
    private final KookMessageSender kookMessageSender; // KOOK消息发送器
    
    /**
     * 构造函数
     * @param qqMessageSender QQ消息发送器
     * @param kookMessageSender KOOK消息发送器
     */
    public HelpHandler(QQMessageSender qqMessageSender, KookMessageSender kookMessageSender) {
        this.qqMessageSender = qqMessageSender;
        this.kookMessageSender = kookMessageSender;
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
     * 构建帮助菜单内容
     */
    private String buildHelpMessage() {
        StringBuilder helpMessage = new StringBuilder();
        helpMessage.append("xiaofanbot帮助菜单\n\n");
        helpMessage.append("可用命令：\n\n");
        
        // 基础功能
        helpMessage.append("【基础功能】\n");
        helpMessage.append("1. oi：基础回复（回复io）\n");
        helpMessage.append("2. 帮助：显示此帮助菜单\n\n");
        
        // 服务器查询
        helpMessage.append("【服务器查询】\n");
        helpMessage.append("3. 人数查询：查询Minecraft服务器在线人数\n\n");
        
        // 签到系统
        helpMessage.append("【签到系统】\n");
        helpMessage.append("4. 签到：每日签到获得积分（24小时冷却）\n");
        helpMessage.append("5. 查询积分：查询当前签到积分\n\n");
        
        // 游戏绑定
        helpMessage.append("【游戏绑定】\n");
        helpMessage.append("6. 绑定：绑定游戏ID到QQ号\n");
        helpMessage.append("7. /mute：屏蔽自己的游戏消息（需先绑定）\n\n");
        
        // 投稿系统
        helpMessage.append("【投稿系统】\n");
        helpMessage.append("8. 投稿 （内容）：投稿内容到数据库（投稿和内容之间必须有空格）\n");
        helpMessage.append("9. tip：随机获取一条投稿内容\n\n");
        
        // 服务器交互
        helpMessage.append("【服务器交互】\n");
        helpMessage.append("10. /c （内容）：发送消息到Minecraft服务器（仅限指定群组）\n\n");
        
        // AI对话
        helpMessage.append("【AI对话（猫娘）】\n");
        helpMessage.append("11. @写了亿小时bug （问题）：群聊中@机器人提问\n");
        helpMessage.append("12. @wans2024 （问题）：群聊中@机器人提问\n");
        helpMessage.append("13. 私聊消息：直接发送消息给机器人（私聊触发）\n");
        helpMessage.append("    ⚠️ 频率限制：每分钟最多10次\n\n");
        
        // 游戏功能（仅QQ支持）
        helpMessage.append("【游戏功能】（仅QQ支持）\n");
        helpMessage.append("14. 游戏菜单：显示游戏菜单\n");
        helpMessage.append("15. 游戏 开始 扫雷 （大小） （雷数）：开始扫雷游戏\n");
        helpMessage.append("    参数：大小6-12，雷数8-15\n");
        helpMessage.append("    示例：游戏 开始 扫雷 9 10\n");
        helpMessage.append("16. 游戏 结束 扫雷：结束当前扫雷游戏\n");
        helpMessage.append("17. 扫雷 揭开 A1：揭开指定位置\n");
        helpMessage.append("18. 扫雷 标记 A1：标记/取消标记指定位置\n\n");
        
        // SSTV功能（仅QQ支持）
        helpMessage.append("【SSTV发射电台】（仅QQ支持）\n");
        helpMessage.append("19. /sstv + 一张图片：发送图片到SSTV发射电台\n");
        helpMessage.append("    说明：图片会自动处理为320x240分辨率\n");
        helpMessage.append("    冷却：成功发送后15分钟冷却（管理员不受限制）\n");
        helpMessage.append("    注意：需要客户端连接到ws://127.0.0.1:2024\n");
        helpMessage.append("20. /sstv 取消冷却：取消当前群的冷却（仅管理员可用）\n\n");
        
        // 管理员功能
        helpMessage.append("【管理员功能】（仅管理员可用）\n");
        helpMessage.append("21. 群聊静默 开启：开启群聊静默模式（不再自动发送消息，屏蔽所有触发词）\n");
        helpMessage.append("22. 群聊静默 关闭：关闭群聊静默模式（恢复正常功能）\n");
        helpMessage.append("23. 黑名单列表：查看所有黑名单QQ号\n");
        helpMessage.append("24. 黑名单删除所有封禁用户投稿：删除所有黑名单用户的投稿\n");
        helpMessage.append("25. /ban (QQ号)：在线拉黑指定QQ号\n");
        
        return helpMessage.toString();
    }
    
    /**
     * 处理帮助请求（QQ消息）
     * @param groupId 群号
     * @param userId QQ号
     * @param messageText 消息内容
     */
    public void handleHelp(long groupId, long userId, String messageText) {
        if (!shouldHandle(messageText)) {
            return;
        }
        
        logger.info("检测到帮助请求，群号: {}, QQ号: {}", groupId, userId);
        
        String helpMessage = buildHelpMessage();
        qqMessageSender.sendGroupMessage(groupId, helpMessage);
        logger.info("帮助菜单已发送，群号: {}, QQ号: {}", groupId, userId);
    }
    
    /**
     * 处理帮助请求（KOOK消息）
     * @param channelId 频道ID（字符串）
     * @param userId 用户ID（字符串）
     * @param messageText 消息内容
     */
    public void handleHelpKook(String channelId, String userId, String messageText) {
        if (!shouldHandle(messageText)) {
            return;
        }
        
        logger.info("检测到帮助请求（KOOK），频道: {}, 用户: {}", channelId, userId);
        
        if (kookMessageSender == null) {
            logger.warn("KOOK消息发送器未配置，无法发送帮助菜单");
            return;
        }
        
        String helpMessage = buildHelpMessage();
        kookMessageSender.sendChannelMessage(channelId, helpMessage);
        logger.info("帮助菜单已发送（KOOK），频道: {}, 用户: {}", channelId, userId);
    }
}
