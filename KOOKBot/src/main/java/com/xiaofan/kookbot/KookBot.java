package com.xiaofan.kookbot;

import okhttp3.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * KOOK机器人核心功能类
 * 包含WebSocket客户端、消息处理、消息发送等所有功能
 */
public class KookBot {
    private static final Logger logger = LoggerFactory.getLogger(KookBot.class);
    
    // 配置常量（从ConfigManager读取）
    public static final String KOOK_API_URL = ConfigManager.getKookApiUrl();
    public static final String KOOK_WS_URL = ConfigManager.getKookWsUrl();
    public static final String KOOK_TOKEN = ConfigManager.getKookBotToken();
    public static final int MAX_PROCESSED_MESSAGE_IDS = 1000;
    public static final long RECONNECT_DELAY_MS = 5000;
    
    // 实例字段
    private final MessageSender messageSender;
    private final MessageHandler messageHandler;
    private final MessageForwarder messageForwarder;
    private final ReplyReceiver replyReceiver; // 接收QQbot的回复消息
    private KookWebSocketClient webSocketClient;
    private ScheduledExecutorService reconnectScheduler; // 定时重连调度器
    private ScheduledFuture<?> reconnectTask; // 定时重连任务
    
    public KookBot() {
        this(KOOK_API_URL, KOOK_WS_URL, KOOK_TOKEN);
    }
    
    public KookBot(String apiUrl, String wsUrl, String token) {
        this.messageSender = new MessageSender(apiUrl, token);
        // 初始化消息转发器（转发到QQbot的WebSocket服务器，端口8848）
        this.messageForwarder = new MessageForwarder();
        this.messageForwarder.connect();
        // 初始化回复接收器（从QQbot的WebSocket服务器接收回复，端口8849）
        this.replyReceiver = new ReplyReceiver(messageSender);
        this.replyReceiver.connect();
        this.messageHandler = new MessageHandler(messageSender, messageForwarder);
        
        // 如果 wsUrl 为空，自动获取 Gateway 地址
        String finalWsUrl = wsUrl;
        if (finalWsUrl == null || finalWsUrl.trim().isEmpty()) {
            logger.info("WebSocket地址未配置，正在从API获取...");
            finalWsUrl = messageSender.getGatewayUrl();
            if (finalWsUrl == null || finalWsUrl.trim().isEmpty()) {
                logger.error("无法获取Gateway地址，请检查Token配置");
                throw new RuntimeException("无法获取Gateway地址");
            }
            logger.info("已获取Gateway地址: {}", finalWsUrl);
        }
        
        this.webSocketClient = new KookWebSocketClient(finalWsUrl, token, messageHandler, messageForwarder);
    }
    
    /**
     * 启动机器人
     */
    public void start() {
        logger.info("启动KOOK机器人...");
        webSocketClient.connect();
        
        // 启动定时重连任务：每分钟强制断开并重新连接，获取新的Gateway
        reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "KOOK-ReconnectScheduler");
            t.setDaemon(true);
            return t;
        });
        
        // 每分钟执行一次强制重连（60秒）
        reconnectTask = reconnectScheduler.scheduleAtFixedRate(() -> {
            try {
                logger.info("[定时重连] 执行定时重连任务，获取新的Gateway并重新连接...");
                // 强制断开当前连接
                if (webSocketClient != null) {
                    webSocketClient.forceReconnect();
                }
            } catch (Exception e) {
                logger.error("[定时重连] 定时重连任务执行失败", e);
            }
        }, 60, 60, TimeUnit.SECONDS);
        
        logger.info("[定时重连] 定时重连任务已启动，每60秒执行一次");
    }
    
    /**
     * 停止机器人
     */
    public void stop() {
        logger.info("停止KOOK机器人...");
        
        // 停止定时重连任务
        if (reconnectTask != null) {
            reconnectTask.cancel(false);
            reconnectTask = null;
        }
        if (reconnectScheduler != null) {
            reconnectScheduler.shutdown();
            try {
                if (!reconnectScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    reconnectScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                reconnectScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            reconnectScheduler = null;
        }
        
        if (webSocketClient != null) {
            webSocketClient.close();
        }
        if (messageForwarder != null) {
            messageForwarder.close();
        }
        if (replyReceiver != null) {
            replyReceiver.close();
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
         * 获取Gateway WebSocket地址
         * 参考文档：https://developer.kookapp.cn/doc/http/gateway#获取网关连接地址
         * @return Gateway WebSocket地址，失败返回null
         */
        public String getGatewayUrl() {
            return getGatewayUrl(1); // 默认压缩
        }
        
        /**
         * 获取Gateway WebSocket地址
         * @param compress 是否压缩，1=压缩，0=不压缩
         * @return Gateway WebSocket地址，失败返回null
         */
        public String getGatewayUrl(int compress) {
            try {
                // 构建请求URL：/api/v3/gateway/index?compress=1
                String url = apiUrl + "/api/v3/gateway/index?compress=" + compress;
                
                Request request = new Request.Builder()
                        .url(url)
                        .method("GET", null)
                        .addHeader("Authorization", "Bot " + token)
                        .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        ResponseBody responseBody = response.body();
                        if (responseBody != null) {
                            String responseString = responseBody.string();
                            logger.debug("Gateway API响应: {}", responseString);
                            
                            JSONObject jsonResponse = new JSONObject(responseString);
                            int code = jsonResponse.optInt("code", -1);
                            
                            if (code == 0) {
                                JSONObject data = jsonResponse.optJSONObject("data");
                                if (data != null) {
                                    String gatewayUrl = data.optString("url", "");
                                    if (!gatewayUrl.isEmpty()) {
                                        logger.info("成功获取Gateway地址: {}", gatewayUrl);
                                        return gatewayUrl;
                                    }
                                }
                            } else {
                                String message = jsonResponse.optString("message", "未知错误");
                                logger.error("获取Gateway地址失败，code: {}, message: {}", code, message);
                            }
                        }
                    } else {
                        logger.error("获取Gateway地址失败，HTTP状态码: {}", response.code());
                        ResponseBody errorBody = response.body();
                        if (errorBody != null) {
                            logger.error("错误响应: {}", errorBody.string());
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("获取Gateway地址时发生异常", e);
            }
            
            return null;
        }
        
        /**
         * 发送频道消息
         * 根据KOOK API文档：https://developer.kookapp.cn/doc/http/message#发送频道聊天消息
         * @param channelId 频道ID（target_id）
         * @param message 消息内容
         * @return 是否发送成功
         */
        public boolean sendChannelMessage(String channelId, String message) {
            try {
                // 根据KOOK API文档，参数名是target_id，不是channel_id
                JSONObject requestJson = new JSONObject();
                requestJson.put("target_id", channelId); // 使用target_id
                requestJson.put("content", message);
                
                RequestBody body = RequestBody.create(requestJson.toString(), JSON);
                Request request = new Request.Builder()
                        .url(apiUrl + "/api/v3/message/create")
                        .method("POST", body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bot " + token)
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
                logger.error("发送频道消息时发生异常", e);
                return false;
            }
        }
    }
    
    /**
     * 消息转发器（转发到QQbot的WebSocket服务器）
     */
    private class MessageForwarder {
        private static final String QQ_BOT_WS_URL = "ws://127.0.0.1:8848";
        private WebSocket webSocket;
        private OkHttpClient client;
        private volatile boolean connected = false;
        
        public MessageForwarder() {
            this.client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();
        }
        
        /**
         * 连接到QQbot的WebSocket服务器
         */
        public void connect() {
            try {
                Request request = new Request.Builder()
                        .url(QQ_BOT_WS_URL)
                        .build();
                
                webSocket = client.newWebSocket(request, new WebSocketListener() {
                    @Override
                    public void onOpen(WebSocket webSocket, Response response) {
                        connected = true;
                        logger.info("[消息转发] 已连接到QQbot WebSocket服务器 (端口8848)");
                    }
                    
                    @Override
                    public void onMessage(WebSocket webSocket, String text) {
                        logger.debug("[消息转发] 收到QQbot响应: {}", text);
                    }
                    
                    @Override
                    public void onClosing(WebSocket webSocket, int code, String reason) {
                        connected = false;
                        logger.warn("[消息转发] 连接正在关闭: code={}, reason={}", code, reason);
                    }
                    
                    @Override
                    public void onClosed(WebSocket webSocket, int code, String reason) {
                        connected = false;
                        logger.warn("[消息转发] 连接已关闭: code={}, reason={}", code, reason);
                        // 尝试重连
                        MessageForwarder.this.scheduleReconnect();
                    }
                    
                    @Override
                    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                        connected = false;
                        logger.warn("[消息转发] 连接失败，QQbot可能未启动: {}", t.getMessage());
                        if (response != null) {
                            logger.warn("[消息转发] 响应状态: {}", response.code());
                        }
                        // 尝试重连
                        MessageForwarder.this.scheduleReconnect();
                    }
                });
            } catch (Exception e) {
                logger.warn("[消息转发] 连接QQbot WebSocket服务器失败: {}", e.getMessage());
            }
        }
        
        /**
         * 转发消息到QQbot
         * 将KOOK消息格式转换为QQbot能理解的格式
         */
        public void forwardMessage(String channelId, String userId, String username, String content, String messageId) {
            if (!connected || webSocket == null) {
                logger.debug("[消息转发] 未连接到QQbot，跳过转发");
                return;
            }
            
            try {
                // 将KOOK消息格式转换为QQbot格式
                // 添加source字段标识消息来源，保留原始KOOK ID
                // QQbot期望的格式：
                // {
                //   "post_type": "message",
                //   "message_type": "group",
                //   "source": "kook",  // 消息来源标识
                //   "group_id": "KOOK频道ID",  // 保留原始KOOK频道ID（字符串）
                //   "user_id": "KOOK用户ID",   // 保留原始KOOK用户ID（字符串）
                //   "raw_message": "签到",
                //   "message": "签到",
                //   "sender": {
                //     "user_id": "KOOK用户ID",
                //     "nickname": "用户名",
                //     "card": "群昵称"
                //   },
                //   "message_id": "KOOK消息ID"
                // }
                
                JSONObject qqBotMessage = new JSONObject();
                qqBotMessage.put("post_type", "message");
                qqBotMessage.put("message_type", "group");
                qqBotMessage.put("source", "kook"); // 标识消息来源
                
                // 保留原始KOOK ID（字符串格式），不使用映射
                qqBotMessage.put("group_id", channelId); // KOOK频道ID
                qqBotMessage.put("user_id", userId);     // KOOK用户ID
                qqBotMessage.put("raw_message", content);
                qqBotMessage.put("message", content);
                qqBotMessage.put("message_id", messageId); // KOOK消息ID
                qqBotMessage.put("message_seq", messageId);
                
                // sender信息（保留原始KOOK用户ID）
                JSONObject sender = new JSONObject();
                sender.put("user_id", userId);
                sender.put("nickname", username);
                sender.put("card", username); // KOOK没有群昵称，使用用户名
                qqBotMessage.put("sender", sender);
                
                // 发送消息
                String messageJson = qqBotMessage.toString();
                boolean sent = webSocket.send(messageJson);
                if (sent) {
                    logger.debug("[消息转发] 消息已转发到QQbot: channelId={}, content={}", channelId, content);
                } else {
                    logger.warn("[消息转发] 消息转发失败: channelId={}", channelId);
                }
            } catch (Exception e) {
                logger.error("[消息转发] 转发消息时发生错误", e);
            }
        }
        
        /**
         * 安排重连
         */
        private void scheduleReconnect() {
            logger.info("[消息转发] 5秒后尝试重新连接...");
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    if (!connected) {
                        logger.info("[消息转发] 正在尝试重新连接...");
                        connect();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("[消息转发] 重连线程被中断");
                }
            }).start();
        }
        
        public boolean isConnected() {
            return connected && webSocket != null;
        }
        
        public void close() {
            connected = false;
            if (webSocket != null) {
                webSocket.close(1000, "正常关闭");
                webSocket = null;
            }
        }
    }
    
    /**
     * 回复接收器（从QQbot接收回复消息并发送到KOOK频道）
     */
    private class ReplyReceiver {
        private static final String QQ_BOT_REPLY_WS_URL = "ws://127.0.0.1:8849";
        private WebSocket webSocket;
        private OkHttpClient client;
        private volatile boolean connected = false;
        private final MessageSender messageSender;
        
        public ReplyReceiver(MessageSender messageSender) {
            this.messageSender = messageSender;
            this.client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();
        }
        
        /**
         * 连接到QQbot的回复WebSocket服务器
         */
        public void connect() {
            try {
                Request request = new Request.Builder()
                        .url(QQ_BOT_REPLY_WS_URL)
                        .build();
                
                webSocket = client.newWebSocket(request, new WebSocketListener() {
                    @Override
                    public void onOpen(WebSocket webSocket, Response response) {
                        connected = true;
                        logger.info("[回复接收] 已连接到QQbot回复服务器 (端口8849)");
                    }
                    
                    @Override
                    public void onMessage(WebSocket webSocket, String text) {
                        try {
                            logger.debug("[回复接收] 收到回复消息: {}", text);
                            handleReplyMessage(text);
                        } catch (Exception e) {
                            logger.error("[回复接收] 处理回复消息时发生错误", e);
                        }
                    }
                    
                    @Override
                    public void onMessage(WebSocket webSocket, okio.ByteString bytes) {
                        try {
                            String text = bytes.utf8();
                            logger.debug("[回复接收] 收到回复消息(二进制): {}", text);
                            handleReplyMessage(text);
                        } catch (Exception e) {
                            logger.error("[回复接收] 处理回复消息时发生错误", e);
                        }
                    }
                    
                    @Override
                    public void onClosing(WebSocket webSocket, int code, String reason) {
                        connected = false;
                        logger.warn("[回复接收] 连接正在关闭: code={}, reason={}", code, reason);
                    }
                    
                    @Override
                    public void onClosed(WebSocket webSocket, int code, String reason) {
                        connected = false;
                        logger.warn("[回复接收] 连接已关闭: code={}, reason={}", code, reason);
                        // 尝试重连
                        scheduleReconnect();
                    }
                    
                    @Override
                    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                        connected = false;
                        logger.warn("[回复接收] 连接失败，QQbot可能未启动: {}", t.getMessage());
                        // 尝试重连
                        scheduleReconnect();
                    }
                });
            } catch (Exception e) {
                logger.warn("[回复接收] 连接QQbot回复服务器失败: {}", e.getMessage());
            }
        }
        
        /**
         * 处理回复消息
         * 消息格式：{"channel_id": "频道ID", "message": "消息内容"}
         */
        private void handleReplyMessage(String text) {
            try {
                JSONObject reply = new JSONObject(text);
                String channelId = reply.optString("channel_id", "");
                String message = reply.optString("message", "");
                
                if (channelId.isEmpty() || message.isEmpty()) {
                    logger.warn("[回复接收] 回复消息格式错误: {}", text);
                    return;
                }
                
                logger.info("[回复接收] 收到回复: channelId={}, message={}", channelId, message);
                
                // 通过KOOK API发送消息到频道
                boolean success = messageSender.sendChannelMessage(channelId, message);
                if (success) {
                    logger.info("[回复接收] 回复消息已发送到KOOK频道: {}", channelId);
                } else {
                    logger.error("[回复接收] 回复消息发送失败: channelId={}", channelId);
                }
            } catch (Exception e) {
                logger.error("[回复接收] 解析回复消息失败: {}", text, e);
            }
        }
        
        /**
         * 安排重连
         */
        private void scheduleReconnect() {
            logger.info("[回复接收] 5秒后尝试重新连接...");
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    if (!connected) {
                        logger.info("[回复接收] 正在尝试重新连接...");
                        connect();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("[回复接收] 重连线程被中断");
                }
            }).start();
        }
        
        public boolean isConnected() {
            return connected && webSocket != null;
        }
        
        public void close() {
            connected = false;
            if (webSocket != null) {
                webSocket.close(1000, "正常关闭");
                webSocket = null;
            }
        }
    }
    
    /**
     * 消息处理器
     */
    private class MessageHandler {
        private final MessageSender messageSender;
        private final MessageForwarder messageForwarder;
        private final Set<String> processedMessageIds = new HashSet<>();
        private volatile int maxSn = 0; // 当前已处理成功的最大的sn序号
        private final Set<Integer> processedSns = new HashSet<>(); // 已处理的sn集合（用于去重）
        private final TreeMap<Integer, JSONObject> messageBuffer = new TreeMap<>(); // 消息暂存区（按sn排序）
        private volatile int expectedSn = 1; // 期望的下一个sn（从1开始，因为sn从1开始）
        private Runnable onHelloReceived; // HELLO事件回调
        private Runnable onReconnectRequired; // RECONNECT事件回调
        
        public MessageHandler(MessageSender messageSender, MessageForwarder messageForwarder) {
            this.messageSender = messageSender;
            this.messageForwarder = messageForwarder;
        }
        
        /**
         * 设置HELLO事件回调
         */
        public void setOnHelloReceived(Runnable callback) {
            this.onHelloReceived = callback;
        }
        
        /**
         * 设置RECONNECT事件回调
         */
        public void setOnReconnectRequired(Runnable callback) {
            this.onReconnectRequired = callback;
        }
        
        /**
         * 清空sn计数和消息队列（收到RECONNECT时调用）
         */
        public void clearSnAndBuffer() {
            synchronized (this) {
                maxSn = 0;
                expectedSn = 1;
                processedSns.clear();
                messageBuffer.clear();
                logger.info("[SN管理] 已清空sn计数和消息队列（RECONNECT）");
            }
        }
        
        /**
         * 获取当前最大sn序号（用于心跳PING）
         */
        public int getMaxSn() {
            return maxSn;
        }
        
        /**
         * 处理WebSocket接收到的消息（字符串格式）
         */
        public void handleWebSocketMessage(String text) {
            try {
                // 先尝试直接解析JSON
                JSONObject event = new JSONObject(text);
                processEvent(event);
            } catch (org.json.JSONException e) {
                // 如果解析失败，可能是压缩数据，尝试解压缩
                logger.debug("直接解析失败，尝试解压缩: {}", e.getMessage());
                try {
                    String decompressed = decompressZlib(text.getBytes("ISO-8859-1"));
                    JSONObject event = new JSONObject(decompressed);
                    processEvent(event);
                } catch (Exception ex) {
                    logger.error("解析WebSocket消息失败（尝试解压缩后）: {}", text.substring(0, Math.min(100, text.length())), ex);
                }
            } catch (Exception e) {
                logger.error("处理WebSocket消息时发生错误: {}", text.substring(0, Math.min(100, text.length())), e);
            }
        }
        
        /**
         * 处理WebSocket接收到的消息（字节数组格式，通常是压缩的）
         */
        public void handleWebSocketMessage(byte[] data) {
            try {
                // 尝试解压缩
                String decompressed = decompressZlib(data);
                logger.debug("解压缩成功，消息长度: {} 字符", decompressed.length());
                JSONObject event = new JSONObject(decompressed);
                processEvent(event);
            } catch (Exception e) {
                logger.error("处理WebSocket二进制消息失败: {} 字节", data.length, e);
            }
        }
        
        /**
         * 处理事件（解压缩后的JSON）
         * 根据KOOK WebSocket协议：https://developer.kookapp.cn/doc/websocket
         */
        private void processEvent(JSONObject event) {
            try {
                // KOOK WebSocket 信令格式：{"s": 信令, "d": 数据, "sn": 序号}
                int signal = event.optInt("s", -1);
                JSONObject data = event.optJSONObject("d");
                int sn = event.optInt("sn", -1);
                
                // 信令说明：
                // s=0: EVENT（消息事件，server->client）
                // s=1: HELLO（握手结果，server->client）
                // s=2: PING（心跳，client->server）
                // s=3: PONG（心跳响应，server->client）
                // s=4: RESUME（恢复会话，client->server）
                // s=5: RECONNECT（要求重连，server->client）
                // s=6: RESUME ACK（恢复确认，server->client）
                
                if (signal == 1) {
                    // HELLO 事件（握手结果）
                    logger.info("收到HELLO事件（握手成功）");
                    if (data != null) {
                        int code = data.optInt("code", -1);
                        if (code == 0) {
                            String sessionId = data.optString("session_id", "");
                            logger.info("握手成功，session_id: {}", sessionId);
                            // TODO: 保存session_id用于resume
                            
                            // 收到HELLO时，重置sn计数（新连接）
                            synchronized (messageHandler) {
                                messageHandler.maxSn = 0;
                                messageHandler.expectedSn = 1;
                                messageHandler.processedSns.clear();
                                messageHandler.messageBuffer.clear();
                                logger.info("[SN管理] 收到HELLO，重置sn计数");
                            }
                            
                            // 启动心跳（通过回调）
                            if (onHelloReceived != null) {
                                onHelloReceived.run();
                            }
                        } else {
                            logger.error("握手失败，错误码: {}", code);
                            // 40100: 缺少参数
                            // 40101: 无效的token
                            // 40102: token验证失败
                            // 40103: token过期
                        }
                    }
                } else if (signal == 0) {
                    // EVENT 事件（消息事件）
                    // 根据KOOK文档，必须按sn顺序处理消息
                    if (sn < 0) {
                        logger.warn("收到EVENT事件但sn无效: {}", sn);
                        return;
                    }
                    
                    logger.debug("收到EVENT事件，sn: {}", sn);
                    
                    // 检查是否已处理过该sn（如果已处理，直接抛弃）
                    synchronized (messageHandler) {
                        if (messageHandler.processedSns.contains(sn)) {
                            logger.debug("收到已处理过的sn消息，直接抛弃: sn={}", sn);
                            return;
                        }
                        
                        // 如果sn等于期望的sn，直接处理
                        if (sn == messageHandler.expectedSn) {
                            // 处理消息
                            if (data != null) {
                                handleEvent(data);
                            } else {
                                logger.warn("EVENT事件数据为空，sn: {}", sn);
                            }
                            
                            // 标记为已处理
                            messageHandler.processedSns.add(sn);
                            messageHandler.maxSn = sn; // 更新最大sn
                            messageHandler.expectedSn = sn + 1; // 期望下一个sn
                            
                            // 处理暂存区中的连续消息
                            processBufferedMessages();
                        } else if (sn > messageHandler.expectedSn) {
                            // sn大于期望值，说明消息乱序，存入暂存区
                            logger.debug("收到乱序消息，存入暂存区: sn={}, 期望sn={}", sn, messageHandler.expectedSn);
                            messageHandler.messageBuffer.put(sn, data);
                        } else {
                            // sn小于期望值，说明是旧消息，直接抛弃
                            logger.debug("收到旧消息（sn小于期望值），直接抛弃: sn={}, 期望sn={}", sn, messageHandler.expectedSn);
                        }
                    }
                } else if (signal == 3) {
                    // PONG 事件（心跳响应）
                    logger.debug("收到PONG心跳响应");
                } else if (signal == 5) {
                    // RECONNECT 事件（要求重连）
                    logger.warn("收到RECONNECT事件，需要重新连接");
                    if (data != null) {
                        int code = data.optInt("code", -1);
                        String err = data.optString("err", "");
                        logger.warn("重连原因: code={}, err={}", code, err);
                    }
                    
                    // 根据KOOK文档，收到RECONNECT时需要清空sn计数和消息队列
                    messageHandler.clearSnAndBuffer();
                    
                    // 立即触发重连（获取新的Gateway）
                    if (onReconnectRequired != null) {
                        logger.info("[RECONNECT] 触发强制重连，获取新的Gateway...");
                        onReconnectRequired.run();
                    } else {
                        logger.error("[RECONNECT] 未设置重连回调，无法自动重连");
                    }
                } else if (signal == 6) {
                    // RESUME ACK 事件（恢复确认）
                    logger.info("收到RESUME ACK，恢复成功");
                    if (data != null) {
                        String sessionId = data.optString("session_id", "");
                        logger.info("恢复后的session_id: {}", sessionId);
                    }
                } else {
                    logger.debug("收到其他信令: s={}, sn={}, data={}", signal, sn, data);
                }
            } catch (Exception e) {
                logger.error("处理事件时发生错误", e);
            }
        }
        
        /**
         * 解压缩zlib数据
         * @param compressed 压缩的字节数组
         * @return 解压缩后的字符串
         */
        private String decompressZlib(byte[] compressed) throws IOException, DataFormatException {
            Inflater inflater = new Inflater();
            inflater.setInput(compressed);
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(compressed.length);
            byte[] buffer = new byte[1024];
            
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }
            
            outputStream.close();
            inflater.end();
            
            return outputStream.toString("UTF-8");
        }
        
        /**
         * 检查消息是否包含@机器人标记
         * 检查是否包含 (met)2357320237(met) 标记
         * 
         * @param content 消息内容
         * @return 如果包含标记返回true，否则返回false
         */
        private boolean containsMentionTag(String content) {
            if (content == null || content.isEmpty()) {
                return false;
            }
            
            // 检查是否包含 (met)2357320237(met) 标记
            return content.contains("(met)2357320237(met)");
        }
        
        /**
         * 移除消息中的@机器人标记
         * 移除格式为 (met)数字(met) 的模式，例如 (met)2357320237(met)
         * 
         * @param content 原始消息内容
         * @return 处理后的消息内容
         */
        private String removeMentionTags(String content) {
            if (content == null || content.isEmpty()) {
                return content;
            }
            
            // 使用正则表达式匹配并移除 (met)数字(met) 模式
            // 匹配模式：\\(met\\)\\d+\\(met\\) 表示 (met)数字(met)
            // 同时移除前后可能的多余空格（使用 \\s* 匹配0个或多个空格）
            String processed = content.replaceAll("\\s*\\(met\\)\\d+\\(met\\)\\s*", " ");
            
            // 清理多余的空格（多个连续空格替换为单个空格）
            processed = processed.replaceAll("\\s+", " ");
            
            // 去除首尾空格
            processed = processed.trim();
            
            // 如果处理后为空，返回原始内容（避免丢失消息）
            if (processed.isEmpty() && !content.trim().isEmpty()) {
                logger.warn("[消息处理] 移除@标记后消息为空，保留原始内容: {}", content);
                return content;
            }
            
            return processed;
        }
        
        /**
         * 处理暂存区中的连续消息
         * 从期望的sn开始，处理暂存区中所有连续的消息
         * 注意：此方法必须在同步块内调用（同步messageHandler对象）
         */
        private void processBufferedMessages() {
            while (!messageBuffer.isEmpty()) {
                Integer nextSn = messageBuffer.firstKey();
                if (nextSn == null || nextSn != expectedSn) {
                    // 暂存区中没有期望的sn，停止处理
                    break;
                }
                
                // 取出并处理消息
                JSONObject bufferedData = messageBuffer.remove(nextSn);
                if (bufferedData != null) {
                    logger.debug("从暂存区处理消息: sn={}", nextSn);
                    handleEvent(bufferedData);
                }
                
                // 标记为已处理
                processedSns.add(nextSn);
                maxSn = nextSn; // 更新最大sn
                expectedSn = nextSn + 1; // 期望下一个sn
            }
        }
        
        /**
         * 处理事件（EVENT信令的d字段）
         * 根据KOOK事件文档，事件数据在d字段中
         * 这个方法接收的data就是EVENT信令的d字段内容
         */
        private void handleEvent(JSONObject data) {
            try {
                if (data == null) {
                    logger.debug("事件数据为空，跳过");
                    return;
                }
                
                // 打印完整事件数据用于调试
                logger.info("收到事件数据: {}", data.toString(2));
                
                // KOOK事件类型可能是字符串或数字
                // 先尝试字符串类型
                String eventTypeStr = data.optString("type", "");
                // 再尝试数字类型
                int eventTypeInt = data.optInt("type", -1);
                
                logger.info("事件类型(字符串): {}, 事件类型(数字): {}", eventTypeStr, eventTypeInt);
                
                // 处理文本消息事件
                // 根据KOOK文档，消息事件类型可能是 "MESSAGE_CREATE" 或数字编码
                // 数字9可能对应MESSAGE_CREATE，需要验证
                if ("MESSAGE_CREATE".equals(eventTypeStr) || eventTypeInt == 9) {
                    logger.info("识别为消息创建事件，开始处理...");
                    logger.info("消息创建事件数据: {}", data.toString(2));
                    handleMessageCreate(data);
                } else {
                    logger.debug("未处理的事件类型: 字符串={}, 数字={}", eventTypeStr, eventTypeInt);
                }
            } catch (Exception e) {
                logger.error("处理事件时发生错误", e);
            }
        }
        
        /**
         * 处理消息创建事件
         * 根据KOOK文档，MESSAGE_CREATE事件的数据结构：
         * d字段直接包含消息数据，不需要再取d字段
         */
        private void handleMessageCreate(JSONObject data) {
            try {
                // 打印完整消息数据用于调试
                logger.info("消息创建事件完整数据: {}", data.toString(2));
                
                // data就是消息数据本身，不需要再取d字段
                // 根据KOOK文档，MESSAGE_CREATE事件的d字段结构：
                // {
                //   "type": "MESSAGE_CREATE" 或 9,
                //   "channel_type": "GROUP",
                //   "target_id": "频道ID",
                //   "author_id": "用户ID",
                //   "content": "消息内容",
                //   "msg_id": "消息ID",
                //   "msg_timestamp": 时间戳,
                //   "nonce": "...",
                //   "extra": {
                //     "author": {用户信息},
                //     ...
                //   }
                // }
                
                // 尝试多种可能的字段名
                String messageId = data.optString("msg_id", "");
                if (messageId.isEmpty()) {
                    messageId = data.optString("id", "");
                }
                
                String channelId = data.optString("target_id", "");
                if (channelId.isEmpty()) {
                    channelId = data.optString("channel_id", "");
                }
                
                String content = data.optString("content", "");
                String authorId = data.optString("author_id", "");
                if (authorId.isEmpty()) {
                    authorId = data.optString("user_id", "");
                }
                
                // 从extra中获取作者信息
                JSONObject extra = data.optJSONObject("extra");
                JSONObject author = null;
                if (extra != null) {
                    author = extra.optJSONObject("author");
                }
                
                // 过滤机器人自身发送的消息
                if (extra != null && extra.optBoolean("bot", false)) {
                    logger.debug("忽略机器人自身发送的消息");
                    return;
                }
                
                if (messageId.isEmpty() || processedMessageIds.contains(messageId)) {
                    logger.debug("消息已处理过，跳过: {}", messageId);
                    return;
                }
                
                // 优先使用author_id，如果没有则从author对象中获取
                String userId = authorId;
                String username = "未知";
                if (author != null) {
                    if (userId.isEmpty()) {
                        userId = author.optString("id", "");
                    }
                    username = author.optString("username", "未知");
                    if (username.isEmpty()) {
                        username = author.optString("nickname", "未知");
                    }
                }
                
                // 如果userId仍然为空，使用author_id
                if (userId.isEmpty() && !authorId.isEmpty()) {
                    userId = authorId;
                }
                
                logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                logger.info("收到频道消息");
                logger.info("频道ID: {}", channelId);
                logger.info("发送者: {} ({})", username, userId);
                logger.info("消息ID: {}", messageId);
                logger.info("消息内容: {}", content);
                logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                
                // 检查消息是否包含@机器人标记 (met)2357320237(met)
                // 只有包含此标记的消息才会被处理并转发
                if (!containsMentionTag(content)) {
                    logger.debug("[消息过滤] 消息不包含@机器人标记，跳过转发: {}", content);
                    return;
                }
                
                // 处理@机器人的消息，移除(met)2357320237(met)模式
                String processedContent = removeMentionTags(content);
                if (!processedContent.equals(content)) {
                    logger.info("[消息处理] 移除@机器人标记，原始内容: {}, 处理后: {}", content, processedContent);
                }
                
                // 转发消息到QQbot处理
                if (messageForwarder != null) {
                    if (messageForwarder.isConnected()) {
                        logger.info("[消息转发] 准备转发消息到QQbot: channelId={}, content={}", channelId, processedContent);
                        messageForwarder.forwardMessage(channelId, userId, username, processedContent, messageId);
                    } else {
                        logger.warn("[消息转发] MessageForwarder未连接，无法转发消息。连接状态: connected={}", messageForwarder.isConnected());
                    }
                } else {
                    logger.error("[消息转发] MessageForwarder为null，无法转发消息");
                }
                
                if (!messageId.isEmpty()) {
                    processedMessageIds.add(messageId);
                    if (processedMessageIds.size() > MAX_PROCESSED_MESSAGE_IDS) {
                        processedMessageIds.clear();
                        logger.debug("已清理消息ID缓存");
                    }
                }
            } catch (Exception e) {
                logger.error("处理消息创建事件时发生错误", e);
            }
        }
    }
    
    /**
     * KOOK WebSocket客户端
     */
    private class KookWebSocketClient {
        private String wsUrl; // 改为非final，允许更新
        private final String token;
        private final MessageHandler messageHandler;
        private final MessageForwarder messageForwarder;
        private WebSocket webSocket;
        private OkHttpClient client;
        private boolean shouldReconnect = true;
        private ScheduledExecutorService heartbeatExecutor;
        private ScheduledFuture<?> heartbeatTask;
        private volatile boolean heartbeatStarted = false;
        private volatile boolean isForceReconnecting = false; // 是否正在强制重连
        private final Random random = new Random();
        
        public KookWebSocketClient(String wsUrl, String token, MessageHandler messageHandler, MessageForwarder messageForwarder) {
            this.wsUrl = wsUrl;
            this.token = token;
            this.messageHandler = messageHandler;
            this.messageForwarder = messageForwarder;
            this.client = createClient();
        }
        
        private OkHttpClient createClient() {
            return new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.SECONDS) // WebSocket read timeout can be infinite
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .pingInterval(30, TimeUnit.SECONDS) // 添加TCP层面的ping，有助于保持连接
                    .retryOnConnectionFailure(true) // 连接失败时自动重试
                    .build();
        }
        
        /**
         * 启动WebSocket连接
         */
        public void connect() {
            shouldReconnect = true;
            connectInternal();
        }
        
        /**
         * 强制重连（获取新的Gateway并重新连接）
         */
        public void forceReconnect() {
            logger.info("[强制重连] 开始强制重连流程...");
            isForceReconnecting = true; // 设置强制重连标志
            
            // 先关闭当前连接
            if (webSocket != null) {
                try {
                    webSocket.close(1000, "强制重连");
                } catch (Exception e) {
                    logger.warn("[强制重连] 关闭旧连接时发生错误", e);
                }
                webSocket = null;
            }
            stopHeartbeat();
            
            // 获取新的Gateway URL
            String newGatewayUrl = messageSender.getGatewayUrl();
            if (newGatewayUrl != null && !newGatewayUrl.trim().isEmpty()) {
                this.wsUrl = newGatewayUrl;
                logger.info("[强制重连] 已获取新的Gateway URL: {}", newGatewayUrl);
            } else {
                logger.warn("[强制重连] 获取新Gateway失败，使用旧的Gateway URL");
            }
            
            // 重新连接
            connectInternal();
            
            // 重置标志（延迟一点，确保连接建立后再重置）
            new Thread(() -> {
                try {
                    Thread.sleep(2000); // 等待2秒
                    isForceReconnecting = false;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
        
        private void connectInternal() {
            // 在连接前获取新的Gateway URL（确保使用最新的token）
            logger.info("[连接] 正在获取最新的Gateway URL...");
            String newGatewayUrl = messageSender.getGatewayUrl();
            if (newGatewayUrl != null && !newGatewayUrl.trim().isEmpty()) {
                this.wsUrl = newGatewayUrl;
                logger.info("[连接] 已获取最新的Gateway URL: {}", newGatewayUrl);
            } else {
                logger.warn("[连接] 获取Gateway失败，使用配置的URL: {}", wsUrl);
            }
            
            String tokenDisplay = (token == null || token.isEmpty() || "YOUR_BOT_TOKEN_HERE".equals(token)) 
                ? "未配置" : "已配置(" + token.length() + "字符)";
            
            // 构建WebSocket URL，添加token参数
            // 根据KOOK文档，连接时需要在URL中添加token参数
            String finalUrl = wsUrl;
            if (!finalUrl.contains("token=")) {
                // 如果URL中没有token参数，添加它
                String separator = finalUrl.contains("?") ? "&" : "?";
                finalUrl = finalUrl + separator + "token=" + token;
            }
            // 添加compress参数（默认压缩）
            if (!finalUrl.contains("compress=")) {
                String separator = finalUrl.contains("?") ? "&" : "?";
                finalUrl = finalUrl + separator + "compress=1";
            }
            
            logger.info("正在连接WebSocket: URL={}, Token={}", finalUrl, tokenDisplay);
            
            Request request = new Request.Builder()
                    .url(finalUrl)
                    .build();
            
            // 设置HELLO事件回调，用于启动心跳
            messageHandler.setOnHelloReceived(() -> {
                logger.info("[心跳] 收到HELLO事件，启动心跳...");
                startHeartbeat();
            });
            
            // 设置RECONNECT事件回调，用于强制重连
            messageHandler.setOnReconnectRequired(() -> {
                logger.info("[RECONNECT] 收到RECONNECT事件，执行强制重连...");
                forceReconnect();
            });
            
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
                        // 二进制消息通常是压缩的（zlib），需要解压缩
                        byte[] data = bytes.toByteArray();
                        logger.debug("收到WebSocket二进制消息: {} 字节", data.length);
                        // 直接传递字节数组给处理器，处理器会尝试解压缩
                        messageHandler.handleWebSocketMessage(data);
                    } catch (Exception e) {
                        logger.error("处理WebSocket二进制消息时发生错误", e);
                    }
                }
                
                @Override
                public void onClosing(WebSocket webSocket, int code, String reason) {
                    // 停止心跳（连接正在关闭）
                    stopHeartbeat();
                    
                    logger.warn("WebSocket正在关闭: code={}, reason={}", code, reason);
                    webSocket.close(1000, null);
                }
                
                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    // 停止心跳（连接已关闭）
                    stopHeartbeat();
                    
                    logger.warn("WebSocket连接已关闭: code={}, reason={}", code, reason);
                    
                    // 如果正在强制重连，不需要再次重连（forceReconnect已经处理了）
                    if (isForceReconnecting) {
                        logger.info("[强制重连] 连接已关闭，强制重连流程已启动，不执行额外重连");
                        return;
                    }
                    
                    // 根据关闭码判断是否需要重连
                    // 1000: 正常关闭
                    // 1001: 端点离开（如服务器关闭或浏览器导航离开）
                    // 1006: 异常关闭（没有收到关闭帧）
                    if (code == 1000) {
                        logger.info("WebSocket正常关闭，不自动重连");
                    } else {
                        if (shouldReconnect) {
                            scheduleReconnect();
                        }
                    }
                }
                
                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    // 停止心跳（连接已断开）
                    stopHeartbeat();
                    
                    // 记录错误信息
                    if (t instanceof java.io.EOFException) {
                        logger.warn("WebSocket连接意外关闭（EOFException），可能是服务器主动断开或网络中断");
                    } else {
                        logger.error("WebSocket连接失败", t);
                    }
                    
                    if (response != null) {
                        logger.error("响应状态: {}", response.code());
                        try {
                            if (response.body() != null) {
                                String body = response.body().string();
                                logger.error("响应体: {}", body);
                            }
                        } catch (IOException e) {
                            logger.debug("无法读取响应体", e);
                        }
                    }
                    
                    if (token == null || token.isEmpty() || "YOUR_BOT_TOKEN_HERE".equals(token)) {
                        logger.error("⚠️ 检测到Token未配置，这可能是连接失败的原因");
                        logger.error("⚠️ 请检查config.properties文件中的kook.bot.token配置");
                    }
                    
                    // 如果应该重连，安排重连
                    if (shouldReconnect) {
                        scheduleReconnect();
                    }
                }
            });
            
            logger.info("WebSocket连接请求已发送");
        }
        
        private void scheduleReconnect() {
            // 确保心跳已停止
            stopHeartbeat();
            
            logger.info("{}秒后尝试重新连接（将获取新的Gateway）...", RECONNECT_DELAY_MS / 1000);
            new Thread(() -> {
                try {
                    Thread.sleep(RECONNECT_DELAY_MS);
                    if (shouldReconnect) {
                        logger.info("[重连] 正在获取新的Gateway URL并重新连接...");
                        // 获取新的Gateway URL
                        String newGatewayUrl = messageSender.getGatewayUrl();
                        if (newGatewayUrl != null && !newGatewayUrl.trim().isEmpty()) {
                            this.wsUrl = newGatewayUrl;
                            logger.info("[重连] 已获取新的Gateway URL: {}", newGatewayUrl);
                        } else {
                            logger.warn("[重连] 获取新Gateway失败，使用旧的Gateway URL: {}", wsUrl);
                        }
                        
                        // 重置心跳状态，准备重新启动
                        heartbeatStarted = false;
                        connectInternal();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("重连线程被中断");
                }
            }).start();
        }
        
        /**
         * 启动心跳（在收到HELLO后调用）
         */
        private void startHeartbeat() {
            if (heartbeatStarted) {
                return;
            }
            heartbeatStarted = true;
            heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "KOOK-Heartbeat");
                t.setDaemon(true);
                return t;
            });
            
            // 根据文档：每隔30秒（随机-5，+5）发送一次PING
            scheduleNextPing();
            logger.info("心跳已启动");
        }
        
        /**
         * 安排下一次PING
         */
        private void scheduleNextPing() {
            if (heartbeatExecutor == null || !heartbeatStarted) {
                return;
            }
            // 下次心跳间隔：30秒 + 随机(-5, +5)秒 = 25-35秒
            long delay = 30000 + (random.nextInt(11) - 5) * 1000;
            heartbeatTask = heartbeatExecutor.schedule(() -> {
                sendPing();
                scheduleNextPing(); // 安排下一次
            }, delay, TimeUnit.MILLISECONDS);
        }
        
        /**
         * 发送PING心跳
         */
        private void sendPing() {
            if (webSocket == null || !heartbeatStarted) {
                return;
            }
            try {
                int currentSn = messageHandler.getMaxSn();
                JSONObject ping = new JSONObject();
                ping.put("s", 2); // 信令2: PING
                ping.put("sn", currentSn);
                
                String pingJson = ping.toString();
                boolean sent = webSocket.send(pingJson);
                if (sent) {
                    logger.debug("发送心跳PING，sn: {}", currentSn);
                } else {
                    logger.warn("发送心跳PING失败");
                }
            } catch (Exception e) {
                logger.error("发送心跳PING时发生错误", e);
            }
        }
        
        /**
         * 停止心跳
         */
        private void stopHeartbeat() {
            heartbeatStarted = false;
            if (heartbeatTask != null) {
                heartbeatTask.cancel(false);
                heartbeatTask = null;
            }
            if (heartbeatExecutor != null) {
                heartbeatExecutor.shutdown();
                try {
                    if (!heartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        heartbeatExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    heartbeatExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                heartbeatExecutor = null;
            }
        }
        
        /**
         * 关闭WebSocket连接
         */
        public void close() {
            shouldReconnect = false;
            stopHeartbeat();
            if (webSocket != null) {
                webSocket.close(1000, "正常关闭");
                webSocket = null;
            }
        }
    }
}

