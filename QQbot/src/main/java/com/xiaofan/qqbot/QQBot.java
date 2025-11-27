package com.xiaofan.qqbot;

import com.xiaofan.qqbot.config.ConfigManager;
import com.xiaofan.qqbot.config.LogConfig;
import com.xiaofan.qqbot.game.MinesweeperGame;
import com.xiaofan.qqbot.game.MinesweeperRenderer;
import com.xiaofan.qqbot.handler.*;
import com.xiaofan.qqbot.manager.*;
import com.xiaofan.qqbot.send.KookMessageSender;
import com.xiaofan.qqbot.send.QQMessageSender;
import com.xiaofan.qqbot.service.CatgirlAIService;
import com.xiaofan.qqbot.service.ServerMessageMonitor;
import com.xiaofan.qqbot.websocket.BindingAPIServer;
import com.xiaofan.qqbot.websocket.KookMessageForwardServer;
import com.xiaofan.qqbot.websocket.KookReplyServer;
import com.xiaofan.qqbot.websocket.SSTVWebSocketServer;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
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
    public static final String NAPCAT_API_URL = com.xiaofan.qqbot.config.ConfigManager.getNapCatApiUrl();
    public static final String NAPCAT_WS_URL = com.xiaofan.qqbot.config.ConfigManager.getNapCatWsUrl();
    public static final String NAPCAT_TOKEN = com.xiaofan.qqbot.config.ConfigManager.getNapCatToken();
    public static final String TRIGGER_MESSAGE = com.xiaofan.qqbot.config.ConfigManager.getTriggerMessage();
    public static final String REPLY_MESSAGE = com.xiaofan.qqbot.config.ConfigManager.getReplyMessage();
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
    private final MuteListManager muteListManager;
    private final BindingHandler bindingHandler;
    private final BindingAPIServer bindingAPIServer;
    private final MuteHandler muteHandler;
    private final MinesweeperHandler minesweeperHandler;
    private final BanHandler banHandler;
    private final SSTVWebSocketServer sstvWebSocketServer;
    private final SSTVHandler sstvHandler;
    private final KookMessageForwardServer kookMessageForwardServer;
    private final KookReplyServer kookReplyServer; // KOOK回复消息WebSocket服务器
    private final SilentModeManager silentModeManager;
    private final SilentModeHandler silentModeHandler;
    private NapCatWebSocketClient webSocketClient;
    // botUserId在CatgirlHandler中管理，不需要在这里存储
    
    public QQBot() {
        this(NAPCAT_API_URL, NAPCAT_WS_URL, NAPCAT_TOKEN);
    }
    
    public QQBot(String apiUrl, String wsUrl, String token) {
        this.messageSender = new MessageSender(apiUrl, token);
        this.banListManager = new BanListManager();
        // 先初始化KOOK回复服务器（需要在创建Handler之前）
        this.kookReplyServer = new KookReplyServer();
        this.kookReplyServer.start();
        
        // 初始化消息发送器
        QQMessageSender qqMessageSender = new QQMessageSender(apiUrl, token);
        KookMessageSender kookMessageSender = new KookMessageSender(kookReplyServer);
        
        this.playerCountQueryHandler = new PlayerCountQueryHandler(qqMessageSender, kookMessageSender);
        this.checkInHandler = new CheckInHandler(qqMessageSender, kookMessageSender);
        this.pointsQueryHandler = new PointsQueryHandler(qqMessageSender, kookMessageSender);
        this.tipSubmissionHandler = new TipSubmissionHandler(qqMessageSender, kookMessageSender);
        this.tipHandler = new TipHandler(qqMessageSender, kookMessageSender);
        this.helpHandler = new HelpHandler(qqMessageSender, kookMessageSender);
        // 初始化猫娘AI处理器（需要先获取botUserId，暂时设为0，会在连接后更新）
        this.catgirlHandler = new CatgirlHandler(
            qqMessageSender,
            kookMessageSender,
            (userId, message) -> messageSender.sendPrivateMessage(userId, message),
            0 // botUserId将在获取后更新
        );
        this.serverCommandHandler = new ServerCommandHandler(
            qqMessageSender,
            kookMessageSender
        );
        this.muteListManager = new MuteListManager();
        this.muteHandler = new MuteHandler(
            qqMessageSender,
            kookMessageSender,
            muteListManager
        );
        // 初始化静默模式管理器（需要在serverMessageMonitor之前初始化）
        this.silentModeManager = new SilentModeManager();
        this.serverMessageMonitor = new ServerMessageMonitor(
            (groupId, message) -> messageSender.sendGroupMessage(groupId, message),
            kookMessageSender,
            muteListManager,
            silentModeManager
        );
        // 初始化绑定功能
        BindingSessionManager sessionManager = BindingSessionManager.getInstance();
        DatabaseManager dbManager = new DatabaseManager();
        this.bindingAPIServer = new BindingAPIServer(sessionManager, dbManager);
        this.bindingAPIServer.initialize(); // 初始化API服务器（但不启用）
        this.bindingHandler = new BindingHandler(
            qqMessageSender,
            kookMessageSender,
            sessionManager,
            bindingAPIServer
        );
        // 初始化扫雷游戏处理器
        String tempImageDir = System.getProperty("user.dir") + File.separator + "temp_images";
        this.minesweeperHandler = new MinesweeperHandler(
            (groupId, message) -> messageSender.sendGroupMessage(groupId, message),
            (groupId, imagePath) -> messageSender.sendGroupImage(groupId, imagePath),
            tempImageDir
        );
        // 初始化黑名单管理处理器
        this.banHandler = new BanHandler(
            qqMessageSender,
            kookMessageSender,
            banListManager,
            dbManager
        );
        // 初始化SSTV WebSocket服务器
        this.sstvWebSocketServer = new SSTVWebSocketServer();
        this.sstvWebSocketServer.start();
        // 初始化SSTV处理器
        this.sstvHandler = new SSTVHandler(
            (groupId, message) -> messageSender.sendGroupMessage(groupId, message),
            sstvWebSocketServer,
            tempImageDir
        );
        // 初始化静默模式处理器（silentModeManager已在上面初始化）
        this.silentModeHandler = new SilentModeHandler(
            (groupId, message) -> messageSender.sendGroupMessage(groupId, message),
            silentModeManager
        );
        this.messageHandler = new MessageHandler(messageSender, playerCountQueryHandler, checkInHandler, pointsQueryHandler, tipSubmissionHandler, tipHandler, helpHandler, catgirlHandler, serverCommandHandler, banListManager, bindingHandler, muteHandler, minesweeperHandler, banHandler, sstvHandler, silentModeManager, silentModeHandler, kookReplyServer);
        // 初始化KOOK消息转发WebSocket服务器（端口8848）
        this.kookMessageForwardServer = new KookMessageForwardServer(messageHandler);
        this.kookMessageForwardServer.start();
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
        // 停止绑定API服务器
        if (bindingAPIServer != null) {
            bindingAPIServer.stop();
        }
        // 停止SSTV WebSocket服务器
        if (sstvWebSocketServer != null) {
            sstvWebSocketServer.stop();
        }
        // 停止KOOK消息转发WebSocket服务器
        if (kookMessageForwardServer != null) {
            kookMessageForwardServer.stop();
        }
        // 停止KOOK回复消息WebSocket服务器
        if (kookReplyServer != null) {
            kookReplyServer.stop();
        }
        // 关闭绑定会话管理器
        BindingSessionManager.getInstance().shutdown();
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
         * 发送群消息（QQ，兼容旧接口）
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
        
        /**
         * 发送群图片
         * @param groupId 群号
         * @param imagePath 图片路径（支持file://、http://、base64://）
         * @return 是否发送成功
         */
        public boolean sendGroupImage(long groupId, String imagePath) {
            try {
                JSONObject requestJson = new JSONObject();
                requestJson.put("group_id", String.valueOf(groupId));
                
                JSONArray messageArray = new JSONArray();
                JSONObject imageSegment = new JSONObject();
                imageSegment.put("type", "image");
                JSONObject imageData = new JSONObject();
                imageData.put("file", imagePath);
                imageData.put("summary", "[图片]");
                imageSegment.put("data", imageData);
                messageArray.put(imageSegment);
                
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
                            logger.info("图片发送成功: {}", responseString);
                        } else {
                            logger.info("图片发送成功（无响应体）");
                        }
                        return true;
                    } else {
                        logger.error("图片发送失败，状态码: {}", response.code());
                        ResponseBody errorBody = response.body();
                        if (errorBody != null) {
                            logger.error("错误响应: {}", errorBody.string());
                        }
                        return false;
                    }
                }
            } catch (Exception e) {
                logger.error("发送群图片时发生异常", e);
                return false;
            }
        }
    }
    
    /**
     * 消息处理器
     */
    public class MessageHandler {
        private final MessageSender messageSender;
        private final KookReplyServer kookReplyServer; // KOOK回复服务器
        private final PlayerCountQueryHandler playerCountQueryHandler;
        private final CheckInHandler checkInHandler;
        private final PointsQueryHandler pointsQueryHandler;
        private final TipSubmissionHandler tipSubmissionHandler;
        private final TipHandler tipHandler;
        private final HelpHandler helpHandler;
        private final CatgirlHandler catgirlHandler;
        private final ServerCommandHandler serverCommandHandler;
        private final BanListManager banListManager;
        private final BindingHandler bindingHandler;
        private final MuteHandler muteHandler;
        private final MinesweeperHandler minesweeperHandler;
        private final BanHandler banHandler;
        private final SSTVHandler sstvHandler;
        private final SilentModeManager silentModeManager;
        private final SilentModeHandler silentModeHandler;
        private final Set<Long> processedMessageIds = new HashSet<>();
        private final Set<String> processedKookMessageIds = new HashSet<>(); // KOOK消息ID去重（字符串）
        
        public MessageHandler(MessageSender messageSender, PlayerCountQueryHandler playerCountQueryHandler, CheckInHandler checkInHandler, PointsQueryHandler pointsQueryHandler, TipSubmissionHandler tipSubmissionHandler, TipHandler tipHandler, HelpHandler helpHandler, CatgirlHandler catgirlHandler, ServerCommandHandler serverCommandHandler, BanListManager banListManager, BindingHandler bindingHandler, MuteHandler muteHandler, MinesweeperHandler minesweeperHandler, BanHandler banHandler, SSTVHandler sstvHandler, SilentModeManager silentModeManager, SilentModeHandler silentModeHandler, KookReplyServer replyServer) {
            this.messageSender = messageSender;
            this.kookReplyServer = replyServer;
            this.playerCountQueryHandler = playerCountQueryHandler;
            this.checkInHandler = checkInHandler;
            this.pointsQueryHandler = pointsQueryHandler;
            this.tipSubmissionHandler = tipSubmissionHandler;
            this.tipHandler = tipHandler;
            this.helpHandler = helpHandler;
            this.catgirlHandler = catgirlHandler;
            this.serverCommandHandler = serverCommandHandler;
            this.banListManager = banListManager;
            this.bindingHandler = bindingHandler;
            this.muteHandler = muteHandler;
            this.minesweeperHandler = minesweeperHandler;
            this.banHandler = banHandler;
            this.sstvHandler = sstvHandler;
            this.silentModeManager = silentModeManager;
            this.silentModeHandler = silentModeHandler;
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
                // 识别消息来源
                String source = event.optString("source", "qq"); // 默认为qq
                boolean isFromKook = "kook".equalsIgnoreCase(source);
                
                // 根据来源处理ID（KOOK使用字符串，QQ使用long）
                String groupIdStr = null;
                long groupId = 0;
                if (isFromKook) {
                    // KOOK消息：使用字符串ID
                    groupIdStr = event.optString("group_id", "");
                } else {
                    // QQ消息：使用long ID
                    groupId = event.optLong("group_id", 0);
                }
                
                String messageIdStr = null;
                long messageId = 0;
                if (isFromKook) {
                    messageIdStr = event.optString("message_id", "");
                    // 使用字符串集合进行去重（KOOK消息ID是UUID，不能转换为long）
                    if (!messageIdStr.isEmpty() && processedKookMessageIds.contains(messageIdStr)) {
                        logger.debug("消息已处理过，跳过: {}", messageIdStr);
                        return;
                    }
                } else {
                    messageId = event.optLong("message_id", 0);
                    if (messageId == 0) {
                        messageId = event.optLong("message_seq", 0);
                    }
                    if (messageId > 0 && processedMessageIds.contains(messageId)) {
                        logger.debug("消息已处理过，跳过: {}", messageId);
                        return;
                    }
                }
                
                JSONObject sender = event.optJSONObject("sender");
                String userIdStr = null;
                long userId = 0;
                String nickname = "未知";
                String card = null;
                
                if (sender != null) {
                    if (isFromKook) {
                        userIdStr = sender.optString("user_id", "");
                    } else {
                        userId = sender.optLong("user_id", 0);
                    }
                    nickname = sender.optString("nickname", "未知");
                    card = sender.optString("card", null);
                }
                
                if (!isFromKook && userId == 0) {
                    userId = event.optLong("user_id", 0);
                }
                if (isFromKook && (userIdStr == null || userIdStr.isEmpty())) {
                    userIdStr = event.optString("user_id", "");
                }
                
                String messageText = extractMessageText(event);
                
                if (messageText.isEmpty()) {
                    logger.debug("消息内容为空，跳过");
                    return;
                }
                
                String displayName = card != null && !card.isEmpty() ? card : nickname;
                
                logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                logger.info("收到群消息 [来源: {}]", isFromKook ? "KOOK" : "QQ");
                if (isFromKook) {
                    logger.info("KOOK频道ID: {}", groupIdStr);
                    logger.info("KOOK用户ID: {}", userIdStr);
                    logger.info("KOOK消息ID: {}", messageIdStr);
                } else {
                    logger.info("QQ群号: {}", groupId);
                    logger.info("QQ号: {}", userId);
                    logger.info("QQ消息ID: {}", messageId > 0 ? messageId : "未知");
                }
                logger.info("发送者: {} ({})", displayName, isFromKook ? userIdStr : userId);
                logger.info("消息内容: {}", messageText);
                logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                
                // 对于KOOK消息，使用字符串ID作为标识符
                // 对于QQ消息，使用long ID
                Object groupIdKey = isFromKook ? groupIdStr : groupId;
                
                // 保存消息来源信息，用于后续回复
                final boolean finalIsFromKook = isFromKook;
                final String finalGroupIdStr = groupIdStr;
                final long finalGroupId = groupId;
                
                // 优先处理群聊静默命令（无论是否在静默模式中，都要先处理这个命令）
                // 这样管理员可以在静默模式下使用"群聊静默 关闭"来恢复正常
                if (silentModeHandler.shouldHandle(messageText)) {
                    if (isFromKook) {
                        logger.info("检测到群聊静默命令 [KOOK]，频道: {}, 用户: {}", groupIdStr, userIdStr);
                        // KOOK消息暂时不支持静默命令，因为需要修改Handler签名
                        logger.warn("KOOK消息暂不支持静默命令功能");
                    } else {
                        logger.info("检测到群聊静默命令 [QQ]，群号: {}, 用户: {}", groupId, userId);
                        silentModeHandler.handleSilentCommand(groupId, userId, messageText);
                    }
                    // 处理完静默命令后，如果是在静默模式下，直接返回，不再处理其他触发词
                    // 注意：如果管理员执行"群聊静默 关闭"，静默模式会被关闭，但这次消息已经处理完毕
                    if (isFromKook) {
                        if (!messageIdStr.isEmpty()) {
                            processedKookMessageIds.add(messageIdStr);
                            if (processedKookMessageIds.size() > MAX_PROCESSED_MESSAGE_IDS) {
                                processedKookMessageIds.clear();
                                logger.debug("已清理KOOK消息ID缓存");
                            }
                        }
                    } else {
                        if (messageId > 0) {
                            processedMessageIds.add(messageId);
                            if (processedMessageIds.size() > MAX_PROCESSED_MESSAGE_IDS) {
                                processedMessageIds.clear();
                                logger.debug("已清理消息ID缓存");
                            }
                        }
                    }
                    return;
                }
                
                // 检查是否处于静默模式
                // 如果在静默模式下，跳过所有其他触发词处理（除了上面的静默命令）
                boolean isSilent = false;
                if (isFromKook) {
                    // KOOK消息暂时不支持静默模式检查
                    isSilent = false;
                } else {
                    isSilent = silentModeManager.isSilent(groupId);
                }
                if (isSilent) {
                    logger.debug("群 {} 处于静默模式，跳过所有触发词处理", groupIdKey);
                    if (isFromKook) {
                        if (!messageIdStr.isEmpty()) {
                            processedKookMessageIds.add(messageIdStr);
                            if (processedKookMessageIds.size() > MAX_PROCESSED_MESSAGE_IDS) {
                                processedKookMessageIds.clear();
                                logger.debug("已清理KOOK消息ID缓存");
                            }
                        }
                    } else {
                        if (messageId > 0) {
                            processedMessageIds.add(messageId);
                            if (processedMessageIds.size() > MAX_PROCESSED_MESSAGE_IDS) {
                                processedMessageIds.clear();
                                logger.debug("已清理消息ID缓存");
                            }
                        }
                    }
                    return;
                }
                
                // 注意：KOOK消息和QQ消息都可以处理，但回复方式不同
                // KOOK消息通过kookReplyServer发送到KOOK频道
                // QQ消息通过messageSender发送到QQ群
                
                // 检查触发词（oi）- 完全匹配
                String trimmedMessage = messageText.trim();
                if (trimmedMessage.equals(TRIGGER_MESSAGE)) {
                    // 如果用户在黑名单中，回复禁止消息
                    if (userId > 0 && banListManager.isBanned(userId)) {
                        logger.warn("检测到黑名单用户发送触发词: {} ({}), 发送禁止消息", displayName, userId);
                        if (finalIsFromKook) {
                            kookReplyServer.sendReply(finalGroupIdStr, banListManager.getBanMessage());
                        } else {
                            messageSender.sendGroupMessage(finalGroupId, banListManager.getBanMessage());
                        }
                    } else {
                        // 正常用户回复普通消息
                        logger.info("检测到触发消息: '{}'，准备在群 {} 回复", TRIGGER_MESSAGE, groupIdKey);
                        if (finalIsFromKook) {
                            kookReplyServer.sendReply(finalGroupIdStr, REPLY_MESSAGE);
                        } else {
                            messageSender.sendGroupMessage(finalGroupId, REPLY_MESSAGE);
                        }
                    }
                }
                
                // 处理人数查询请求
                if (playerCountQueryHandler.shouldHandle(messageText)) {
                    // 如果用户在黑名单中，回复禁止消息
                    if (userId > 0 && banListManager.isBanned(userId)) {
                        logger.warn("检测到黑名单用户发送触发词(人数查询): {} ({}), 发送禁止消息", displayName, userId);
                        if (finalIsFromKook) {
                            kookReplyServer.sendReply(finalGroupIdStr, banListManager.getBanMessage());
                        } else {
                            messageSender.sendGroupMessage(finalGroupId, banListManager.getBanMessage());
                        }
                    } else {
                        logger.info("检测到人数查询请求，群号: {}", groupIdKey);
                        if (finalIsFromKook) {
                            playerCountQueryHandler.handleQueryKook(finalGroupIdStr, messageText);
                        } else {
                            playerCountQueryHandler.handleQuery(finalGroupId, messageText);
                        }
                    }
                }
                
                // 处理签到请求
                if (checkInHandler.shouldHandle(messageText)) {
                    // 如果用户在黑名单中，回复禁止消息
                    if (userId > 0 && banListManager.isBanned(userId)) {
                        logger.warn("检测到黑名单用户发送触发词(签到): {} ({}), 发送禁止消息", displayName, userId);
                        if (finalIsFromKook) {
                            kookReplyServer.sendReply(finalGroupIdStr, banListManager.getBanMessage());
                        } else {
                            messageSender.sendGroupMessage(finalGroupId, banListManager.getBanMessage());
                        }
                    } else {
                        logger.info("检测到签到请求，群号: {}, 用户: {}", groupIdKey, isFromKook ? userIdStr : userId);
                        if (finalIsFromKook) {
                            checkInHandler.handleCheckInKook(finalGroupIdStr, userIdStr, messageText);
                        } else {
                            checkInHandler.handleCheckIn(finalGroupId, userId, messageText);
                        }
                    }
                }
                
                // 处理积分查询请求
                if (pointsQueryHandler.shouldHandle(messageText)) {
                    // 如果用户在黑名单中，回复禁止消息
                    if (userId > 0 && banListManager.isBanned(userId)) {
                        logger.warn("检测到黑名单用户发送触发词(查询积分): {} ({}), 发送禁止消息", displayName, userId);
                        if (finalIsFromKook) {
                            kookReplyServer.sendReply(finalGroupIdStr, banListManager.getBanMessage());
                        } else {
                            messageSender.sendGroupMessage(finalGroupId, banListManager.getBanMessage());
                        }
                    } else {
                        logger.info("检测到积分查询请求，群号: {}, 用户: {}", groupIdKey, isFromKook ? userIdStr : userId);
                        if (finalIsFromKook) {
                            pointsQueryHandler.handleQueryKook(finalGroupIdStr, userIdStr, messageText);
                        } else {
                            pointsQueryHandler.handleQuery(finalGroupId, userId, messageText);
                        }
                    }
                }
                
                // 处理投稿请求
                if (tipSubmissionHandler.shouldHandle(messageText)) {
                    // 如果用户在黑名单中，回复禁止消息
                    if (userId > 0 && banListManager.isBanned(userId)) {
                        logger.warn("检测到黑名单用户发送触发词(投稿): {} ({}), 发送禁止消息", displayName, userId);
                        if (finalIsFromKook) {
                            kookReplyServer.sendReply(finalGroupIdStr, banListManager.getBanMessage());
                        } else {
                            messageSender.sendGroupMessage(finalGroupId, banListManager.getBanMessage());
                        }
                    } else {
                        logger.info("检测到投稿请求，群号: {}, 用户: {}", groupIdKey, isFromKook ? userIdStr : userId);
                        if (finalIsFromKook) {
                            tipSubmissionHandler.handleSubmissionKook(finalGroupIdStr, userIdStr, messageText);
                        } else {
                            tipSubmissionHandler.handleSubmission(finalGroupId, userId, messageText);
                        }
                    }
                }
                
                // 处理tip请求
                if (tipHandler.shouldHandle(messageText)) {
                    // 如果用户在黑名单中，回复禁止消息
                    if (userId > 0 && banListManager.isBanned(userId)) {
                        logger.warn("检测到黑名单用户发送触发词(tip): {} ({}), 发送禁止消息", displayName, userId);
                        if (finalIsFromKook) {
                            kookReplyServer.sendReply(finalGroupIdStr, banListManager.getBanMessage());
                        } else {
                            messageSender.sendGroupMessage(finalGroupId, banListManager.getBanMessage());
                        }
                    } else {
                        logger.info("检测到tip请求，群号: {}, 用户: {}", groupIdKey, isFromKook ? userIdStr : userId);
                        if (finalIsFromKook) {
                            tipHandler.handleTipKook(finalGroupIdStr, userIdStr, messageText);
                        } else {
                            tipHandler.handleTip(finalGroupId, userId, messageText);
                        }
                    }
                }
                
                // 处理帮助请求
                if (helpHandler.shouldHandle(messageText)) {
                    // 如果用户在黑名单中，回复禁止消息
                    if (userId > 0 && banListManager.isBanned(userId)) {
                        logger.warn("检测到黑名单用户发送触发词(帮助): {} ({}), 发送禁止消息", displayName, userId);
                        if (finalIsFromKook) {
                            kookReplyServer.sendReply(finalGroupIdStr, banListManager.getBanMessage());
                        } else {
                            messageSender.sendGroupMessage(finalGroupId, banListManager.getBanMessage());
                        }
                    } else {
                        logger.info("检测到帮助请求，群号: {}, 用户: {}", groupIdKey, isFromKook ? userIdStr : userId);
                        if (finalIsFromKook) {
                            helpHandler.handleHelpKook(finalGroupIdStr, userIdStr, messageText);
                        } else {
                            helpHandler.handleHelp(finalGroupId, userId, messageText);
                        }
                    }
                }
                
                // 处理绑定请求
                if (bindingHandler.shouldHandle(messageText)) {
                    // 如果用户在黑名单中，回复禁止消息
                    if (userId > 0 && banListManager.isBanned(userId)) {
                        logger.warn("检测到黑名单用户发送触发词(绑定): {} ({}), 发送禁止消息", displayName, userId);
                        if (finalIsFromKook) {
                            kookReplyServer.sendReply(finalGroupIdStr, banListManager.getBanMessage());
                        } else {
                            messageSender.sendGroupMessage(finalGroupId, banListManager.getBanMessage());
                        }
                    } else {
                        logger.info("检测到绑定请求，群号: {}, 用户: {}", groupIdKey, isFromKook ? userIdStr : userId);
                        if (finalIsFromKook) {
                            bindingHandler.handleBindingKook(finalGroupIdStr, userIdStr, messageText);
                        } else {
                            bindingHandler.handleBinding(finalGroupId, userId, messageText);
                        }
                    }
                }
                
                // 处理 /mute 命令（屏蔽）
                if (muteHandler.shouldHandle(messageText)) {
                    // 如果用户在黑名单中，回复禁止消息
                    if (userId > 0 && banListManager.isBanned(userId)) {
                        logger.warn("检测到黑名单用户发送触发词(/mute): {} ({}), 发送禁止消息", displayName, userId);
                        if (finalIsFromKook) {
                            kookReplyServer.sendReply(finalGroupIdStr, banListManager.getBanMessage());
                        } else {
                            messageSender.sendGroupMessage(finalGroupId, banListManager.getBanMessage());
                        }
                    } else {
                        logger.info("检测到屏蔽请求，群号: {}, 用户: {}", groupIdKey, isFromKook ? userIdStr : userId);
                        if (finalIsFromKook) {
                            muteHandler.handleMuteKook(finalGroupIdStr, userIdStr, messageText);
                        } else {
                            muteHandler.handleMute(finalGroupId, userId, messageText);
                        }
                    }
                }
                
                // 处理 /c 命令（服务器命令）
                if (serverCommandHandler.shouldHandle(messageText)) {
                    // 如果用户在黑名单中，回复禁止消息
                    if (userId > 0 && banListManager.isBanned(userId)) {
                        logger.warn("检测到黑名单用户发送触发词(/c命令): {} ({}), 发送禁止消息", displayName, userId);
                        if (finalIsFromKook) {
                            kookReplyServer.sendReply(finalGroupIdStr, banListManager.getBanMessage());
                        } else {
                            messageSender.sendGroupMessage(finalGroupId, banListManager.getBanMessage());
                        }
                    } else {
                        logger.info("检测到 /c 命令，群号: {}, 用户: {}", groupIdKey, displayName);
                        if (finalIsFromKook) {
                            serverCommandHandler.handleCommandKook(finalGroupIdStr, userIdStr, displayName, messageText);
                        } else {
                            serverCommandHandler.handleCommand(finalGroupId, userId, displayName, messageText);
                        }
                    }
                }
                
                // 处理@机器人的消息（猫娘AI）
                boolean shouldHandleCatgirl = false;
                if (finalIsFromKook) {
                    shouldHandleCatgirl = catgirlHandler.shouldHandleGroupMessageKook(event);
                } else {
                    shouldHandleCatgirl = catgirlHandler.shouldHandleGroupMessage(event);
                }
                
                if (shouldHandleCatgirl) {
                    // 如果用户在黑名单中，回复禁止消息
                    if (userId > 0 && banListManager.isBanned(userId)) {
                        logger.warn("检测到黑名单用户@机器人: {} ({}), 发送禁止消息", displayName, userId);
                        if (finalIsFromKook) {
                            kookReplyServer.sendReply(finalGroupIdStr, banListManager.getBanMessage());
                        } else {
                            messageSender.sendGroupMessage(finalGroupId, banListManager.getBanMessage());
                        }
                    } else {
                        logger.info("检测到@机器人消息，群号: {}, 用户: {}", groupIdKey, isFromKook ? userIdStr : userId);
                        if (finalIsFromKook) {
                            catgirlHandler.handleGroupMessageKook(finalGroupIdStr, userIdStr, event);
                        } else {
                            catgirlHandler.handleGroupMessage(finalGroupId, userId, event);
                        }
                    }
                }
                
                // 处理扫雷游戏消息（KOOK消息禁止使用游戏功能，避免频繁发送导致封禁）
                if (minesweeperHandler.shouldHandle(messageText)) {
                    // KOOK消息禁止使用游戏功能
                    if (finalIsFromKook) {
                        logger.warn("KOOK消息禁止使用游戏功能，已跳过: 频道ID={}, 用户={}, 消息={}", finalGroupIdStr, userIdStr, messageText);
                        // 记录消息ID，避免重复处理
                        if (!messageIdStr.isEmpty()) {
                            processedKookMessageIds.add(messageIdStr);
                        }
                        return; // 直接返回，不处理游戏功能
                    }
                    
                    // 如果用户在黑名单中，回复禁止消息
                    if (userId > 0 && banListManager.isBanned(userId)) {
                        logger.warn("检测到黑名单用户发送游戏消息: {} ({}), 发送禁止消息", displayName, userId);
                        messageSender.sendGroupMessage(finalGroupId, banListManager.getBanMessage());
                    } else {
                        logger.info("检测到游戏消息，群号: {}, 用户: {}", groupIdKey, userId);
                        minesweeperHandler.handleMessage(finalGroupId, userId, messageText);
                    }
                }
                
                // 处理黑名单管理命令
                if (banHandler.shouldHandle(messageText)) {
                    logger.info("检测到黑名单管理命令，群号: {}, 用户: {}", groupIdKey, isFromKook ? userIdStr : userId);
                    if (finalIsFromKook) {
                        // 将KOOK的userId转换为long
                        long userIdLong = 0;
                        try {
                            userIdLong = Long.parseLong(userIdStr);
                        } catch (NumberFormatException e) {
                            logger.error("无法将KOOK用户ID转换为long: {}", userIdStr);
                            kookReplyServer.sendReply(finalGroupIdStr, "命令执行失败：无效的用户ID");
                            return;
                        }
                        banHandler.handleBanCommandKook(finalGroupIdStr, userIdLong, messageText);
                    } else {
                        banHandler.handleBanCommand(finalGroupId, userId, messageText);
                    }
                }
                
                // 注意：群聊静默命令已经在上面优先处理了，这里不再重复处理
                
                // 处理SSTV命令（KOOK消息禁止使用SSTV功能，避免频繁发送导致封禁）
                if (sstvHandler.shouldHandle(messageText, event)) {
                    // KOOK消息禁止使用SSTV功能
                    if (finalIsFromKook) {
                        logger.warn("KOOK消息禁止使用SSTV功能，已跳过: 频道ID={}, 用户={}, 消息={}", finalGroupIdStr, userIdStr, messageText);
                        // 记录消息ID，避免重复处理
                        if (!messageIdStr.isEmpty()) {
                            processedKookMessageIds.add(messageIdStr);
                        }
                        return; // 直接返回，不处理SSTV功能
                    }
                    
                    // 如果用户在黑名单中，回复禁止消息
                    if (userId > 0 && banListManager.isBanned(userId)) {
                        logger.warn("检测到黑名单用户发送SSTV命令: {} ({}), 发送禁止消息", displayName, userId);
                        messageSender.sendGroupMessage(finalGroupId, banListManager.getBanMessage());
                    } else {
                        logger.info("检测到SSTV命令，群号: {}, 用户: {}", groupIdKey, userId);
                        sstvHandler.handleSSTV(finalGroupId, userId, messageText, event);
                    }
                }
                
                // 记录消息ID，避免重复处理
                    if (isFromKook) {
                        if (!messageIdStr.isEmpty()) {
                            processedKookMessageIds.add(messageIdStr);
                            if (processedKookMessageIds.size() > MAX_PROCESSED_MESSAGE_IDS) {
                                processedKookMessageIds.clear();
                                logger.debug("已清理KOOK消息ID缓存");
                            }
                        }
                    } else {
                    if (messageId > 0) {
                        processedMessageIds.add(messageId);
                        if (processedMessageIds.size() > MAX_PROCESSED_MESSAGE_IDS) {
                            processedMessageIds.clear();
                            logger.debug("已清理消息ID缓存");
                        }
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
            // 记录连接信息（不记录完整Token）
            String tokenDisplay = (token == null || token.isEmpty() || "YOUR_TOKEN_HERE".equals(token)) 
                ? "未配置" : "已配置(" + token.length() + "字符)";
            logger.info("正在连接WebSocket: URL={}, Token={}", wsUrl, tokenDisplay);
            
            Request request = new Request.Builder()
                    .url(wsUrl)
                    .addHeader("Authorization", "Bearer " + token)
                    .build();
            
            webSocket = client.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    logger.info("WebSocket连接已建立");
                    logger.info("响应状态: {}", response.code());
                    logger.info("响应头: {}", response.headers());
                    if (response.body() != null) {
                        try {
                            String body = response.body().string();
                            if (body != null && !body.isEmpty()) {
                                logger.info("响应体: {}", body);
                            }
                        } catch (IOException e) {
                            logger.debug("无法读取响应体", e);
                        }
                    }
                    // 检查Token是否有效
                    if (token == null || token.isEmpty() || "YOUR_TOKEN_HERE".equals(token)) {
                        logger.error("⚠️ WebSocket Token未配置或使用默认值，连接可能被服务器拒绝");
                        logger.error("⚠️ 请在config.properties中配置正确的napcat.token");
                    }
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
                    // code=1005 表示"No Status Received"，通常表示服务器端主动关闭连接
                    if (code == 1005) {
                        logger.error("⚠️ WebSocket被服务器端关闭（code=1005），可能的原因：");
                        logger.error("⚠️ 1. Token验证失败 - 请检查config.properties中的napcat.token是否正确");
                        logger.error("⚠️ 2. NapCat服务未正确启动或配置");
                        logger.error("⚠️ 3. WebSocket URL不正确 - 当前URL: {}", wsUrl);
                        if (token == null || token.isEmpty() || "YOUR_TOKEN_HERE".equals(token)) {
                            logger.error("⚠️ 当前Token: {} (未配置或使用默认值)", 
                                token == null || token.isEmpty() ? "空" : "YOUR_TOKEN_HERE");
                        }
                    }
                    webSocket.close(1000, null);
                }
                
                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    logger.warn("WebSocket连接已关闭: code={}, reason={}", code, reason);
                    if (code == 1005) {
                        logger.error("⚠️ 连接关闭代码1005通常表示认证失败，请检查Token配置");
                    }
                    if (shouldReconnect) {
                        scheduleReconnect();
                    }
                }
                
                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    logger.error("WebSocket连接失败", t);
                    if (response != null) {
                        logger.error("响应状态: {}", response.code());
                        logger.error("响应头: {}", response.headers());
                        try {
                            if (response.body() != null) {
                                String body = response.body().string();
                                logger.error("响应体: {}", body);
                            }
                        } catch (IOException e) {
                            logger.error("无法读取响应体", e);
                        }
                    }
                    // 检查Token配置
                    if (token == null || token.isEmpty() || "YOUR_TOKEN_HERE".equals(token)) {
                        logger.error("⚠️ 检测到Token未配置，这可能是连接失败的原因");
                        logger.error("⚠️ 请检查config.properties文件中的napcat.token配置");
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

