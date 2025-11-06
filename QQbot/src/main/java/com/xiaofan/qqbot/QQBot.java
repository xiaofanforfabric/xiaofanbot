package com.xiaofan.qqbot;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * QQ机器人核心功能类
 * 包含WebSocket客户端、消息处理、消息发送等所有功能
 */
public class QQBot {
    private static final Logger logger = LoggerFactory.getLogger(QQBot.class);
    
    // 配置常量（从ConfigManager读取）
    public static final String NAPCAT_API_URL = ConfigManager.getNapCatApiUrl();
    public static final String NAPCAT_WS_URL = ConfigManager.getNapCatWsUrl();
    public static final String NAPCAT_TOKEN = ConfigManager.getNapCatToken();
    public static final String TRIGGER_MESSAGE = ConfigManager.getTriggerMessage();
    public static final String REPLY_MESSAGE = ConfigManager.getReplyMessage();
    public static final int MAX_PROCESSED_MESSAGE_IDS = 1000;
    public static final long RECONNECT_DELAY_MS = 5000;
    
    // 实例字段
    private final MessageSender messageSender;
    private final MessageHandler messageHandler;
    private final PlayerCountQueryHandler playerCountQueryHandler;
    private final CheckInHandler checkInHandler;
    private final PointsQueryHandler pointsQueryHandler;
    private final TipSubmissionHandler tipSubmissionHandler;
    private final TipHandler tipHandler;
    private final HelpHandler helpHandler;
    private final CatgirlHandler catgirlHandler;
    private final ServerCommandHandler serverCommandHandler;
    private final ServerMessageMonitor serverMessageMonitor;
    private final BanListManager banListManager;
    private NapCatWebSocketClient webSocketClient;
    // botUserId在CatgirlHandler中管理，不需要在这里存储
    
    public QQBot() {
        this(NAPCAT_API_URL, NAPCAT_WS_URL, NAPCAT_TOKEN);
    }
    
    public QQBot(String apiUrl, String wsUrl, String token) {
        this.messageSender = new MessageSender(apiUrl, token);
        this.banListManager = new BanListManager();
        this.playerCountQueryHandler = new PlayerCountQueryHandler(
            (groupId, message) -> messageSender.sendGroupMessage(groupId, message)
        );
        this.checkInHandler = new CheckInHandler(
            (groupId, message) -> messageSender.sendGroupMessage(groupId, message)
        );
        this.pointsQueryHandler = new PointsQueryHandler(
            (groupId, message) -> messageSender.sendGroupMessage(groupId, message)
        );
        this.tipSubmissionHandler = new TipSubmissionHandler(
            (groupId, message) -> messageSender.sendGroupMessage(groupId, message)
        );
        this.tipHandler = new TipHandler(
            (groupId, message) -> messageSender.sendGroupMessage(groupId, message)
        );
        this.helpHandler = new HelpHandler(
            (groupId, message) -> messageSender.sendGroupMessage(groupId, message)
        );
        // 初始化猫娘AI处理器（需要先获取botUserId，暂时设为0，会在连接后更新）
        this.catgirlHandler = new CatgirlHandler(
            (groupId, message) -> messageSender.sendGroupMessage(groupId, message),
            (userId, message) -> messageSender.sendPrivateMessage(userId, message),
            0 // botUserId将在获取后更新
        );
        this.serverCommandHandler = new ServerCommandHandler(
            (groupId, message) -> messageSender.sendGroupMessage(groupId, message)
        );
        this.serverMessageMonitor = new ServerMessageMonitor(
            (groupId, message) -> messageSender.sendGroupMessage(groupId, message)
        );
        this.messageHandler = new MessageHandler(messageSender, playerCountQueryHandler, checkInHandler, pointsQueryHandler, tipSubmissionHandler, tipHandler, helpHandler, catgirlHandler, serverCommandHandler, banListManager);
        this.webSocketClient = new NapCatWebSocketClient(wsUrl, token, messageHandler);
    }
    
    /**
     * 启动机器人
     */
    public void start() {
        logger.info("启动QQ机器人...");
        webSocketClient.connect();
        // 启动服务器消息监控（自动同步服务器消息）
        serverMessageMonitor.start();
    }
    
    /**
     * 停止机器人
     */
    public void stop() {
        logger.info("停止QQ机器人...");
        // 停止服务器消息监控
        serverMessageMonitor.stop();
        if (webSocketClient != null) {
            webSocketClient.close();
        }
    }
    
    /**
     * 消息发送器
     */
    private class MessageSender {
        private final OkHttpClient httpClient;
        private final MediaType JSON = MediaType.get("application/json; charset=utf-8");
        private final String apiUrl;
        private final String token;
        
        public MessageSender(String apiUrl, String token) {
            this.apiUrl = apiUrl;
            this.token = token;
            this.httpClient = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();
        }
        
        /**
         * 发送群消息
         */
        public boolean sendGroupMessage(long groupId, String message) {
            try {
                JSONObject requestJson = new JSONObject();
                requestJson.put("group_id", String.valueOf(groupId));
                
                JSONArray messageArray = new JSONArray();
                JSONObject textSegment = new JSONObject();
                textSegment.put("type", "text");
                JSONObject textData = new JSONObject();
                textData.put("text", message);
                textSegment.put("data", textData);
                messageArray.put(textSegment);
                
                requestJson.put("message", messageArray);
                
                RequestBody body = RequestBody.create(requestJson.toString(), JSON);
                Request request = new Request.Builder()
                        .url(apiUrl + "/send_group_msg")
                        .method("POST", body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bearer " + token)
                        .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        ResponseBody responseBody = response.body();
                        if (responseBody != null) {
                            String responseString = responseBody.string();
                            logger.info("消息发送成功: {}", responseString);
                        } else {
                            logger.info("消息发送成功（无响应体）");
                        }
                        return true;
                    } else {
                        logger.error("消息发送失败，状态码: {}", response.code());
                        ResponseBody errorBody = response.body();
                        if (errorBody != null) {
                            logger.error("错误响应: {}", errorBody.string());
                        }
                        return false;
                    }
                }
            } catch (Exception e) {
                logger.error("发送群消息时发生异常", e);
                return false;
            }
        }
        
        /**
         * 发送私聊消息
         */
        public boolean sendPrivateMessage(long userId, String message) {
            try {
                JSONObject requestJson = new JSONObject();
                requestJson.put("user_id", String.valueOf(userId));
                
                JSONArray messageArray = new JSONArray();
                JSONObject textSegment = new JSONObject();
                textSegment.put("type", "text");
                JSONObject textData = new JSONObject();
                textData.put("text", message);
                textSegment.put("data", textData);
                messageArray.put(textSegment);
                
                requestJson.put("message", messageArray);
                
                RequestBody body = RequestBody.create(requestJson.toString(), JSON);
                Request request = new Request.Builder()
                        .url(apiUrl + "/send_private_msg")
                        .method("POST", body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bearer " + token)
                        .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        ResponseBody responseBody = response.body();
                        if (responseBody != null) {
                            String responseString = responseBody.string();
                            logger.info("私聊消息发送成功: {}", responseString);
                        } else {
                            logger.info("私聊消息发送成功（无响应体）");
                        }
                        return true;
                    } else {
                        logger.error("私聊消息发送失败，状态码: {}", response.code());
                        ResponseBody errorBody = response.body();
                        if (errorBody != null) {
                            logger.error("错误响应: {}", errorBody.string());
                        }
                        return false;
                    }
                }
            } catch (Exception e) {
                logger.error("发送私聊消息时发生异常", e);
                return false;
            }
        }
    }
    
    /**
     * 消息处理器
     */
    private class MessageHandler {
        private final MessageSender messageSender;
        private final PlayerCountQueryHandler playerCountQueryHandler;
        private final CheckInHandler checkInHandler;
        private final PointsQueryHandler pointsQueryHandler;
        private final TipSubmissionHandler tipSubmissionHandler;
        private final TipHandler tipHandler;
        private final HelpHandler helpHandler;
        private final CatgirlHandler catgirlHandler;
        private final ServerCommandHandler serverCommandHandler;
        private final BanListManager banListManager;
        private final Set<Long> processedMessageIds = new HashSet<>();
        
        public MessageHandler(MessageSender messageSender, PlayerCountQueryHandler playerCountQueryHandler, CheckInHandler checkInHandler, PointsQueryHandler pointsQueryHandler, TipSubmissionHandler tipSubmissionHandler, TipHandler tipHandler, HelpHandler helpHandler, CatgirlHandler catgirlHandler, ServerCommandHandler serverCommandHandler, BanListManager banListManager) {
            this.messageSender = messageSender;
            this.playerCountQueryHandler = playerCountQueryHandler;
            this.checkInHandler = checkInHandler;
            this.pointsQueryHandler = pointsQueryHandler;
            this.tipSubmissionHandler = tipSubmissionHandler;
            this.tipHandler = tipHandler;
            this.helpHandler = helpHandler;
            this.catgirlHandler = catgirlHandler;
            this.serverCommandHandler = serverCommandHandler;
            this.banListManager = banListManager;
        }
        
        /**
         * 处理WebSocket接收到的消息
         */
        public void handleWebSocketMessage(String text) {
            try {
                JSONObject event = new JSONObject(text);
                
                String postType = event.optString("post_type", "");
                String messageType = event.optString("message_type", "");
                String noticeType = event.optString("notice_type", "");
                
                if ("message".equals(postType) && "group".equals(messageType)) {
                    handleGroupMessageEvent(event);
                } else if ("message".equals(postType) && "private".equals(messageType)) {
                    handlePrivateMessageEvent(event);
                } else if ("message_sent".equals(postType) && "group".equals(messageType)) {
                    logger.debug("忽略自己发送的消息");
                } else if ("meta_event".equals(postType) && "heartbeat".equals(event.optString("meta_event_type", ""))) {
                    logger.debug("收到心跳事件");
                } else if ("meta_event".equals(postType) && "lifecycle".equals(event.optString("meta_event_type", ""))) {
                    logger.info("收到生命周期事件: {}", event.optString("sub_type", ""));
                } else {
                    logger.debug("收到其他类型事件: post_type={}, message_type={}, notice_type={}", 
                            postType, messageType, noticeType);
                }
            } catch (Exception e) {
                logger.error("解析WebSocket消息失败: {}", text, e);
            }
        }
        
        /**
         * 处理群消息事件
         */
        private void handleGroupMessageEvent(JSONObject event) {
            try {
                long groupId = event.optLong("group_id", 0);
                
                long messageId = event.optLong("message_id", 0);
                if (messageId == 0) {
                    messageId = event.optLong("message_seq", 0);
                }
                
                if (messageId > 0 && processedMessageIds.contains(messageId)) {
                    logger.debug("消息已处理过，跳过: {}", messageId);
                    return;
                }
                
                JSONObject sender = event.optJSONObject("sender");
                long userId = 0;
                String nickname = "未知";
                String card = null;
                
                if (sender != null) {
                    userId = sender.optLong("user_id", 0);
                    nickname = sender.optString("nickname", "未知");
                    card = sender.optString("card", null);
                }
                
                if (userId == 0) {
                    userId = event.optLong("user_id", 0);
                }
                
                String messageText = extractMessageText(event);
                
                if (messageText.isEmpty()) {
                    logger.debug("消息内容为空，跳过");
                    return;
                }
                
                String displayName = card != null && !card.isEmpty() ? card : nickname;
                
                logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                logger.info("收到群消息");
                logger.info("群号: {}", groupId);
                logger.info("发送者: {} ({})", displayName, userId);
                logger.info("消息ID: {}", messageId > 0 ? messageId : "未知");
                logger.info("消息内容: {}", messageText);
                logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                
                // 检查触发词（oi）- 完全匹配
                String trimmedMessage = messageText.trim();
                if (trimmedMessage.equals(TRIGGER_MESSAGE)) {
                    // 如果用户在黑名单中，回复禁止消息
                    if (userId > 0 && banListManager.isBanned(userId)) {
                        logger.warn("检测到黑名单用户发送触发词: {} ({}), 发送禁止消息", displayName, userId);
                        messageSender.sendGroupMessage(groupId, banListManager.getBanMessage());
                    } else {
                        // 正常用户回复普通消息
                        logger.info("检测到触发消息: '{}'，准备在群 {} 回复", TRIGGER_MESSAGE, groupId);
                        messageSender.sendGroupMessage(groupId, REPLY_MESSAGE);
                    }
                }
                
                // 处理人数查询请求
                if (playerCountQueryHandler.shouldHandle(messageText)) {
                    // 如果用户在黑名单中，回复禁止消息
                    if (userId > 0 && banListManager.isBanned(userId)) {
                        logger.warn("检测到黑名单用户发送触发词(人数查询): {} ({}), 发送禁止消息", displayName, userId);
                        messageSender.sendGroupMessage(groupId, banListManager.getBanMessage());
                    } else {
                        logger.info("检测到人数查询请求，群号: {}", groupId);
                        playerCountQueryHandler.handleQuery(groupId, messageText);
                    }
                }
                
                // 处理签到请求
                if (checkInHandler.shouldHandle(messageText)) {
                    // 如果用户在黑名单中，回复禁止消息
                    if (userId > 0 && banListManager.isBanned(userId)) {
                        logger.warn("检测到黑名单用户发送触发词(签到): {} ({}), 发送禁止消息", displayName, userId);
                        messageSender.sendGroupMessage(groupId, banListManager.getBanMessage());
                    } else {
                        logger.info("检测到签到请求，群号: {}, QQ号: {}", groupId, userId);
                        checkInHandler.handleCheckIn(groupId, userId, messageText);
                    }
                }
                
                // 处理积分查询请求
                if (pointsQueryHandler.shouldHandle(messageText)) {
                    // 如果用户在黑名单中，回复禁止消息
                    if (userId > 0 && banListManager.isBanned(userId)) {
                        logger.warn("检测到黑名单用户发送触发词(查询积分): {} ({}), 发送禁止消息", displayName, userId);
                        messageSender.sendGroupMessage(groupId, banListManager.getBanMessage());
                    } else {
                        logger.info("检测到积分查询请求，群号: {}, QQ号: {}", groupId, userId);
                        pointsQueryHandler.handleQuery(groupId, userId, messageText);
                    }
                }
                
                // 处理投稿请求
                if (tipSubmissionHandler.shouldHandle(messageText)) {
                    // 如果用户在黑名单中，回复禁止消息
                    if (userId > 0 && banListManager.isBanned(userId)) {
                        logger.warn("检测到黑名单用户发送触发词(投稿): {} ({}), 发送禁止消息", displayName, userId);
                        messageSender.sendGroupMessage(groupId, banListManager.getBanMessage());
                    } else {
                        logger.info("检测到投稿请求，群号: {}, QQ号: {}", groupId, userId);
                        tipSubmissionHandler.handleSubmission(groupId, userId, messageText);
                    }
                }
                
                // 处理tip请求
                if (tipHandler.shouldHandle(messageText)) {
                    // 如果用户在黑名单中，回复禁止消息
                    if (userId > 0 && banListManager.isBanned(userId)) {
                        logger.warn("检测到黑名单用户发送触发词(tip): {} ({}), 发送禁止消息", displayName, userId);
                        messageSender.sendGroupMessage(groupId, banListManager.getBanMessage());
                    } else {
                        logger.info("检测到tip请求，群号: {}, QQ号: {}", groupId, userId);
                        tipHandler.handleTip(groupId, userId, messageText);
                    }
                }
                
                // 处理帮助请求
                if (helpHandler.shouldHandle(messageText)) {
                    // 如果用户在黑名单中，回复禁止消息
                    if (userId > 0 && banListManager.isBanned(userId)) {
                        logger.warn("检测到黑名单用户发送触发词(帮助): {} ({}), 发送禁止消息", displayName, userId);
                        messageSender.sendGroupMessage(groupId, banListManager.getBanMessage());
                    } else {
                        logger.info("检测到帮助请求，群号: {}, QQ号: {}", groupId, userId);
                        helpHandler.handleHelp(groupId, userId, messageText);
                    }
                }
                
                // 处理 /c 命令（服务器命令）
                if (serverCommandHandler.shouldHandle(messageText)) {
                    // 如果用户在黑名单中，回复禁止消息
                    if (userId > 0 && banListManager.isBanned(userId)) {
                        logger.warn("检测到黑名单用户发送触发词(/c命令): {} ({}), 发送禁止消息", displayName, userId);
                        messageSender.sendGroupMessage(groupId, banListManager.getBanMessage());
                    } else {
                        logger.info("检测到 /c 命令，群号: {}, 用户: {}", groupId, displayName);
                        serverCommandHandler.handleCommand(groupId, userId, displayName, messageText);
                    }
                }
                
                // 处理@机器人的消息（猫娘AI）
                if (catgirlHandler.shouldHandleGroupMessage(event)) {
                    // 如果用户在黑名单中，回复禁止消息
                    if (userId > 0 && banListManager.isBanned(userId)) {
                        logger.warn("检测到黑名单用户@机器人: {} ({}), 发送禁止消息", displayName, userId);
                        messageSender.sendGroupMessage(groupId, banListManager.getBanMessage());
                    } else {
                        logger.info("检测到@机器人消息，群号: {}, 用户: {}", groupId, userId);
                        catgirlHandler.handleGroupMessage(groupId, userId, event);
                    }
                }
                
                if (messageId > 0) {
                    processedMessageIds.add(messageId);
                    if (processedMessageIds.size() > MAX_PROCESSED_MESSAGE_IDS) {
                        processedMessageIds.clear();
                        logger.debug("已清理消息ID缓存");
                    }
                }
            } catch (Exception e) {
                logger.error("处理群消息事件时发生错误", e);
            }
        }
        
        /**
         * 从事件中提取消息文本
         */
        private String extractMessageText(JSONObject event) {
            String rawMessage = event.optString("raw_message", "");
            String messageText = "";
            
            if (!rawMessage.isEmpty()) {
                messageText = rawMessage;
                messageText = messageText.replaceAll("\\[CQ:[^]]+\\]", "").trim();
            } else {
                try {
                    Object messageObj = event.get("message");
                    if (messageObj instanceof String) {
                        messageText = (String) messageObj;
                    } else if (messageObj instanceof JSONArray) {
                        JSONArray messageArray = (JSONArray) messageObj;
                        StringBuilder textBuilder = new StringBuilder();
                        for (int i = 0; i < messageArray.length(); i++) {
                            JSONObject segment = messageArray.getJSONObject(i);
                            String type = segment.optString("type", "");
                            if ("text".equals(type)) {
                                JSONObject data = segment.optJSONObject("data");
                                if (data != null && data.has("text")) {
                                    textBuilder.append(data.getString("text"));
                                }
                            } else {
                                textBuilder.append("[").append(type).append("]");
                            }
                        }
                        messageText = textBuilder.toString().trim();
                    }
                } catch (Exception e) {
                    logger.warn("解析message字段失败，尝试直接获取字符串", e);
                    messageText = event.optString("message", "").trim();
                }
            }
            
            return messageText;
        }
        
        /**
         * 处理私聊消息事件
         */
        private void handlePrivateMessageEvent(JSONObject event) {
            try {
                long userId = event.optLong("user_id", 0);
                
                long messageId = event.optLong("message_id", 0);
                if (messageId == 0) {
                    messageId = event.optLong("message_seq", 0);
                }
                
                if (messageId > 0 && processedMessageIds.contains(messageId)) {
                    logger.debug("私聊消息已处理过，跳过: {}", messageId);
                    return;
                }
                
                JSONObject sender = event.optJSONObject("sender");
                String nickname = "未知";
                
                if (sender != null) {
                    userId = sender.optLong("user_id", userId);
                    nickname = sender.optString("nickname", "未知");
                }
                
                if (userId == 0) {
                    logger.warn("私聊消息中无法获取用户ID，跳过");
                    return;
                }
                
                String messageText = extractMessageText(event);
                
                if (messageText.isEmpty()) {
                    logger.debug("私聊消息内容为空，跳过");
                    return;
                }
                
                logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                logger.info("收到私聊消息");
                logger.info("发送者: {} ({})", nickname, userId);
                logger.info("消息ID: {}", messageId > 0 ? messageId : "未知");
                logger.info("消息内容: {}", messageText);
                logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                
                // 处理私聊消息（猫娘AI）
                if (catgirlHandler.shouldHandlePrivateMessage(event)) {
                    // 如果用户在黑名单中，回复禁止消息
                    if (userId > 0 && banListManager.isBanned(userId)) {
                        logger.warn("检测到黑名单用户私聊: {} ({}), 发送禁止消息", nickname, userId);
                        messageSender.sendPrivateMessage(userId, banListManager.getBanMessage());
                    } else {
                        logger.info("检测到私聊消息，用户: {}", userId);
                        catgirlHandler.handlePrivateMessage(userId, messageText);
                    }
                }
                
                if (messageId > 0) {
                    processedMessageIds.add(messageId);
                    if (processedMessageIds.size() > MAX_PROCESSED_MESSAGE_IDS) {
                        processedMessageIds.clear();
                        logger.debug("已清理消息ID缓存");
                    }
                }
            } catch (Exception e) {
                logger.error("处理私聊消息事件时发生错误", e);
            }
        }
    }
    
    /**
     * NapCat WebSocket客户端
     */
    private class NapCatWebSocketClient {
        private final String wsUrl;
        private final String token;
        private final MessageHandler messageHandler;
        private WebSocket webSocket;
        private OkHttpClient client;
        private boolean shouldReconnect = true;
        
        public NapCatWebSocketClient(String wsUrl, String token, MessageHandler messageHandler) {
            this.wsUrl = wsUrl;
            this.token = token;
            this.messageHandler = messageHandler;
            this.client = createClient();
        }
        
        private OkHttpClient createClient() {
            return new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();
        }
        
        /**
         * 启动WebSocket连接
         */
        public void connect() {
            shouldReconnect = true;
            connectInternal();
        }
        
        private void connectInternal() {
            Request request = new Request.Builder()
                    .url(wsUrl)
                    .addHeader("Authorization", "Bearer " + token)
                    .build();
            
            webSocket = client.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    logger.info("WebSocket连接已建立");
                    logger.info("响应状态: {}", response.code());
                }
                
                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    try {
                        logger.debug("收到WebSocket消息: {}", text);
                        messageHandler.handleWebSocketMessage(text);
                    } catch (Exception e) {
                        logger.error("处理WebSocket消息时发生错误", e);
                    }
                }
                
                @Override
                public void onMessage(WebSocket webSocket, okio.ByteString bytes) {
                    try {
                        String text = bytes.utf8();
                        logger.debug("收到WebSocket二进制消息: {}", text);
                        messageHandler.handleWebSocketMessage(text);
                    } catch (Exception e) {
                        logger.error("处理WebSocket二进制消息时发生错误", e);
                    }
                }
                
                @Override
                public void onClosing(WebSocket webSocket, int code, String reason) {
                    logger.warn("WebSocket正在关闭: code={}, reason={}", code, reason);
                    webSocket.close(1000, null);
                }
                
                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    logger.warn("WebSocket连接已关闭: code={}, reason={}", code, reason);
                    if (shouldReconnect) {
                        scheduleReconnect();
                    }
                }
                
                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    logger.error("WebSocket连接失败", t);
                    if (response != null) {
                        logger.error("响应状态: {}, 响应体: {}", response.code(), response.body());
                    }
                    if (shouldReconnect) {
                        scheduleReconnect();
                    }
                }
            });
            
            logger.info("WebSocket连接请求已发送");
        }
        
        private void scheduleReconnect() {
            logger.info("{}秒后尝试重新连接...", RECONNECT_DELAY_MS / 1000);
            new Thread(() -> {
                try {
                    Thread.sleep(RECONNECT_DELAY_MS);
                    if (shouldReconnect) {
                        logger.info("正在尝试重新连接...");
                        connectInternal();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("重连线程被中断");
                }
            }).start();
        }
        
        /**
         * 关闭WebSocket连接
         */
        public void close() {
            shouldReconnect = false;
            if (webSocket != null) {
                webSocket.close(1000, "正常关闭");
                webSocket = null;
            }
        }
    }
}

