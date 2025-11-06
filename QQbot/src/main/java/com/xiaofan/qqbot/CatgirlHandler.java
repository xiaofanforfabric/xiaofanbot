package com.xiaofan.qqbot;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

/**
 * 猫娘AI处理器
 * 处理@机器人的群消息和私聊消息
 */
public class CatgirlHandler {
    private static final Logger logger = LoggerFactory.getLogger(CatgirlHandler.class);
    
    // 频率限制配置
    private static final int RATE_LIMIT_MAX_REQUESTS = 10; // 每分钟最多10次
    private static final long RATE_LIMIT_TIME_WINDOW = 60000; // 1分钟（毫秒）
    
    // 频率限制相关变量
    private static final AtomicInteger requestCount = new AtomicInteger(0);
    private static volatile long lastResetTime = System.currentTimeMillis();
    // 用户请求时间记录（预留，用于未来可能的用户级频率限制）
    // private static final ConcurrentHashMap<Long, Long> userLastRequestTime = new ConcurrentHashMap<>();
    
    private final BiFunction<Long, String, Boolean> messageSender;
    private final BiFunction<Long, String, Boolean> privateMessageSender; // 私聊消息发送器
    private volatile long botUserId; // 机器人自己的QQ号，用于检测@消息（使用volatile支持多线程更新）
    
    /**
     * 构造函数
     * @param messageSender 群消息发送函数
     * @param privateMessageSender 私聊消息发送函数
     * @param botUserId 机器人自己的QQ号（初始值，如果为0则接受所有@消息）
     */
    public CatgirlHandler(BiFunction<Long, String, Boolean> messageSender, 
                         BiFunction<Long, String, Boolean> privateMessageSender,
                         long botUserId) {
        this.messageSender = messageSender;
        this.privateMessageSender = privateMessageSender;
        this.botUserId = botUserId;
    }
    
    /**
     * 设置机器人自己的QQ号（可选，如果未设置则接受所有@消息）
     */
    public void setBotUserId(long botUserId) {
        this.botUserId = botUserId;
    }
    
    // 允许的机器人名称（严格模式）
    private static final String[] ALLOWED_BOT_NAMES = {"写了亿小时bug", "wans2024"};
    
    /**
     * 检查群消息是否@了机器人（严格模式）
     * 仅开头包含@，且@后面必须是"写了亿小时bug"或"wans2024"才触发
     * @param event 群消息事件
     * @return 如果@了机器人返回true，并提取问题内容
     */
    public boolean shouldHandleGroupMessage(JSONObject event) {
        try {
            // 只检查message字段（数组格式），必须包含type="at"的segment
            Object messageObj = event.opt("message");
            if (!(messageObj instanceof JSONArray)) {
                return false;
            }
            
            JSONArray messageArray = (JSONArray) messageObj;
            if (messageArray.length() == 0) {
                return false;
            }
            
            // 严格模式：@必须在消息开头
            JSONObject firstSegment = messageArray.getJSONObject(0);
            String firstType = firstSegment.optString("type", "");
            
            // 第一个segment必须是at类型
            if (!"at".equals(firstType)) {
                return false;
            }
            
            // 检查@的是否是允许的机器人名称
            JSONObject atData = firstSegment.optJSONObject("data");
            if (atData == null) {
                return false;
            }
            
            // 获取@的用户名（昵称或备注）
            String atName = atData.optString("name", "");
            // 如果name为空，尝试从其他字段获取
            if (atName.isEmpty()) {
                atName = atData.optString("qq", "");
            }
            
            // 检查是否匹配允许的机器人名称
            boolean isAllowedBot = false;
            for (String allowedName : ALLOWED_BOT_NAMES) {
                if (allowedName.equals(atName)) {
                    isAllowedBot = true;
                    break;
                }
            }
            
            if (!isAllowedBot) {
                // 如果名称不匹配，也检查QQ号（如果botUserId已设置）
                long atUserId = atData.optLong("qq", 0);
                if (botUserId != 0 && atUserId == botUserId) {
                    // 如果QQ号匹配，也允许（但优先检查名称）
                    isAllowedBot = true;
                } else {
                    // 名称和QQ号都不匹配，不触发
                    return false;
                }
            }
            
            // 提取@后面的问题内容
            StringBuilder questionBuilder = new StringBuilder();
            // 从第二个segment开始提取文本（第一个是@）
            for (int i = 1; i < messageArray.length(); i++) {
                JSONObject segment = messageArray.getJSONObject(i);
                String type = segment.optString("type", "");
                
                if ("text".equals(type)) {
                    JSONObject data = segment.optJSONObject("data");
                    if (data != null && data.has("text")) {
                        questionBuilder.append(data.getString("text"));
                    }
                }
            }
            
            String question = questionBuilder.toString().trim();
            
            // 必须有问题内容才处理
            if (question.isEmpty()) {
                return false;
            }
            
            // 在事件中存储提取的问题，供后续使用
            event.put("_catgirl_question", question);
            logger.debug("严格模式@检测通过，@名称: {}, 问题: {}", atName, question);
            return true;
            
        } catch (Exception e) {
            logger.error("检查@消息时发生异常", e);
            return false;
        }
    }
    
    /**
     * 检查是否是私聊消息
     * @param event 消息事件
     * @return 如果是私聊返回true
     */
    public boolean shouldHandlePrivateMessage(JSONObject event) {
        String postType = event.optString("post_type", "");
        String messageType = event.optString("message_type", "");
        
        return "message".equals(postType) && "private".equals(messageType);
    }
    
    /**
     * 检查频率限制
     * @return 如果超过限制返回true
     */
    private boolean checkRateLimit() {
        long currentTime = System.currentTimeMillis();
        
        // 检查是否需要重置计数器
        if (currentTime - lastResetTime >= RATE_LIMIT_TIME_WINDOW) {
            requestCount.set(0);
            lastResetTime = currentTime;
        }
        
        // 检查是否超过限制
        int currentCount = requestCount.getAndIncrement();
        if (currentCount >= RATE_LIMIT_MAX_REQUESTS) {
            logger.warn("频率限制：当前请求数 {}/{}", currentCount + 1, RATE_LIMIT_MAX_REQUESTS);
            return true;
        }
        
        return false;
    }
    
    /**
     * 处理群消息中的@机器人
     * @param groupId 群号
     * @param userId 用户QQ号
     * @param event 消息事件
     */
    public void handleGroupMessage(long groupId, long userId, JSONObject event) {
        // 检查频率限制
        if (checkRateLimit()) {
            messageSender.apply(groupId, "调用过于频繁，请一分钟后再试。");
            return;
        }
        
        // 提取问题
        String question = event.optString("_catgirl_question", "");
        if (question.isEmpty()) {
            logger.warn("@消息中未找到问题内容");
            return;
        }
        
        logger.info("检测到@机器人消息，群号: {}, 用户: {}, 问题: {}", groupId, userId, question);
        
        // 异步处理AI请求
        new Thread(() -> {
            try {
                String aiResponse = CatgirlAIService.getAIResponse(question);
                
                if (aiResponse != null && !aiResponse.isEmpty()) {
                    messageSender.apply(groupId, aiResponse);
                    logger.info("猫娘AI回复成功，群号: {}, 用户: {}", groupId, userId);
                } else {
                    messageSender.apply(groupId, "抱歉，我现在无法回答，请稍后再试喵~");
                    logger.warn("AI回复为空，群号: {}, 用户: {}", groupId, userId);
                }
            } catch (Exception e) {
                logger.error("处理AI请求时发生异常，群号: {}, 用户: {}", groupId, userId, e);
                messageSender.apply(groupId, "抱歉，处理你的消息时出错了喵~");
            }
        }).start();
    }
    
    /**
     * 处理私聊消息
     * @param userId 用户QQ号
     * @param messageText 消息内容
     */
    public void handlePrivateMessage(long userId, String messageText) {
        // 检查频率限制
        if (checkRateLimit()) {
            privateMessageSender.apply(userId, "调用过于频繁，请一分钟后再试。");
            return;
        }
        
        if (messageText == null || messageText.trim().isEmpty()) {
            return;
        }
        
        logger.info("检测到私聊消息，用户: {}, 内容: {}", userId, messageText);
        
        // 异步处理AI请求
        new Thread(() -> {
            try {
                String aiResponse = CatgirlAIService.getAIResponse(messageText.trim());
                
                if (aiResponse != null && !aiResponse.isEmpty()) {
                    privateMessageSender.apply(userId, aiResponse);
                    logger.info("猫娘AI私聊回复成功，用户: {}", userId);
                } else {
                    privateMessageSender.apply(userId, "抱歉，我现在无法回答，请稍后再试喵~");
                    logger.warn("AI回复为空，用户: {}", userId);
                }
            } catch (Exception e) {
                logger.error("处理AI私聊请求时发生异常，用户: {}", userId, e);
                privateMessageSender.apply(userId, "抱歉，处理你的消息时出错了喵~");
            }
        }).start();
    }
}

