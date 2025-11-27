package com.xiaofan.qqbot.service;

import com.xiaofan.qqbot.manager.MuteListManager;
import com.xiaofan.qqbot.manager.SilentModeManager;
import com.xiaofan.qqbot.send.KookMessageSender;
import okhttp3.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * 服务器消息监控器
 * 定期从Minecraft客户端API获取服务器聊天消息并发送到QQ群
 */
public class ServerMessageMonitor {
    private static final Logger logger = LoggerFactory.getLogger(ServerMessageMonitor.class);
    
    // Minecraft客户端API地址
    private static final String SERVER_MESSAGE_API_URL = "http://127.0.0.1:2000/get_server_last_message";
    private static final long POLL_INTERVAL_SECONDS = 3; // 每3秒轮询一次
    
    // 目标QQ群列表
    private static final List<Long> TARGET_GROUPS = Arrays.asList(
        1067452253L,
        721103774L,
        1055829026L
    );
    
    // 目标KOOK频道列表（字符串ID）
    private static final List<String> TARGET_KOOK_CHANNELS = Arrays.asList(
        "1365216370308850", // 原有频道
        "9787965173633251", // 新增频道1
        "2136763741769464"  // 新增频道2
    );
    
    // 消息前缀
    private static final String MESSAGE_PREFIX = "邦国崛起服务器消息：";
    
    private final OkHttpClient httpClient;
    private final BiFunction<Long, String, Boolean> messageSender;
    private final KookMessageSender kookMessageSender;
    private final MuteListManager muteListManager;
    private final SilentModeManager silentModeManager;
    private ScheduledExecutorService scheduler;
    private boolean isRunning = false;
    
    /**
     * 构造函数（旧版本，兼容性）
     * @param messageSender 消息发送函数，接收群号和消息内容，返回是否发送成功
     * @param muteListManager 屏蔽列表管理器
     * @param silentModeManager 静默模式管理器
     */
    public ServerMessageMonitor(BiFunction<Long, String, Boolean> messageSender,
                               MuteListManager muteListManager,
                               SilentModeManager silentModeManager) {
        this.messageSender = messageSender;
        this.kookMessageSender = null;
        this.muteListManager = muteListManager;
        this.silentModeManager = silentModeManager;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * 构造函数（新版本，支持KOOK）
     * @param messageSender QQ消息发送函数
     * @param kookMessageSender KOOK消息发送器
     * @param muteListManager 屏蔽列表管理器
     * @param silentModeManager 静默模式管理器
     */
    public ServerMessageMonitor(BiFunction<Long, String, Boolean> messageSender,
                               KookMessageSender kookMessageSender,
                               MuteListManager muteListManager,
                               SilentModeManager silentModeManager) {
        this.messageSender = messageSender;
        this.kookMessageSender = kookMessageSender;
        this.muteListManager = muteListManager;
        this.silentModeManager = silentModeManager;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * 启动监控
     */
    public void start() {
        if (isRunning) {
            logger.warn("[服务器消息监控] 已经在运行中");
            return;
        }
        
        isRunning = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ServerMessageMonitor");
            t.setDaemon(true);
            return t;
        });
        
        // 立即执行一次，然后每3秒执行一次
        scheduler.scheduleAtFixedRate(this::pollAndSend, 0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
        
        logger.info("[服务器消息监控] 已启动，每{}秒轮询一次API", POLL_INTERVAL_SECONDS);
        logger.info("[服务器消息监控] 目标QQ群: {}", TARGET_GROUPS);
        if (kookMessageSender != null) {
            logger.info("[服务器消息监控] 目标KOOK频道: {}", TARGET_KOOK_CHANNELS);
        }
    }
    
    /**
     * 停止监控
     */
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        logger.info("[服务器消息监控] 已停止");
    }
    
    /**
     * 轮询API并发送消息
     */
    private void pollAndSend() {
        try {
            String message = fetchServerMessage();
            
            if (message != null && !message.trim().isEmpty()) {
                // 检查消息是否包含被屏蔽的游戏ID
                if (muteListManager.containsMutedGameId(message)) {
                    logger.debug("[服务器消息监控] 消息包含被屏蔽的游戏ID，已过滤: {}", message);
                    return; // 直接返回，不发送消息
                }
                
                // 解析消息来源并格式化
                String formattedMessage = formatServerMessage(message);
                
                // 发送到QQ群
                for (Long groupId : TARGET_GROUPS) {
                    try {
                        // 检查群是否处于静默模式
                        if (silentModeManager.isSilent(groupId)) {
                            logger.debug("[服务器消息监控] 群 {} 处于静默模式，跳过发送消息", groupId);
                            continue;
                        }
                        
                        Boolean success = messageSender.apply(groupId, formattedMessage);
                        if (success != null && success) {
                            logger.info("[服务器消息监控] 消息已发送到群 {}: {}", groupId, message);
                        } else {
                            logger.warn("[服务器消息监控] 消息发送失败，群号: {}", groupId);
                        }
                    } catch (Exception e) {
                        logger.error("[服务器消息监控] 发送消息到群 {} 时发生错误", groupId, e);
                    }
                }
                
                // 发送到KOOK频道
                if (kookMessageSender != null) {
                    for (String channelId : TARGET_KOOK_CHANNELS) {
                        try {
                            boolean success = kookMessageSender.sendChannelMessage(channelId, formattedMessage);
                            if (success) {
                                logger.info("[服务器消息监控] 消息已发送到KOOK频道 {}: {}", channelId, message);
                            } else {
                                logger.warn("[服务器消息监控] 消息发送失败，KOOK频道: {}", channelId);
                            }
                        } catch (Exception e) {
                            logger.error("[服务器消息监控] 发送消息到KOOK频道 {} 时发生错误", channelId, e);
                        }
                    }
                }
            } else {
                // null或空消息，只记录日志（debug级别）
                logger.debug("[服务器消息监控] 未获取到有效消息（返回null或空）");
            }
            
        } catch (Exception e) {
            // 异常情况只记录日志，不发送
            logger.debug("[服务器消息监控] 轮询API时发生异常: {}", e.getMessage());
        }
    }
    
    /**
     * 格式化服务器消息
     * 解析消息来源（QQ或KOOK）并格式化输出
     * @param message 原始消息
     * @return 格式化后的消息
     */
    private String formatServerMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return MESSAGE_PREFIX + message;
        }
        
        // 检测消息来源标识
        // 格式可能是：xiaofanbot: QQ消息：用户：程序员[KOOK] 程序员: 测试
        // 需要提取：xiaofanbot: KOOK消息：用户： 程序员: 测试
        
        String formatted = message;
        
        // 检查是否包含 [KOOK] 标识（优先级最高，因为可能混在QQ消息中）
        if (message.contains("[KOOK]")) {
            // 格式：xiaofanbot: QQ消息：用户：程序员[KOOK] 程序员: 测试
            // 需要提取：xiaofanbot: KOOK消息：用户： 程序员: 测试
            
            int kookIndex = message.indexOf("[KOOK]");
            String beforeKook = message.substring(0, kookIndex);
            String afterKook = message.substring(kookIndex + 7); // "[KOOK] " 长度为7
            
            // 查找冒号后的实际消息内容
            int colonIndex = afterKook.indexOf(":");
            if (colonIndex > 0) {
                String userPart = afterKook.substring(0, colonIndex).trim();
                String contentPart = afterKook.substring(colonIndex + 1).trim();
                
                // 提取前缀（xiaofanbot:）
                String prefix = "";
                int firstColonIndex = beforeKook.indexOf(":");
                if (firstColonIndex > 0) {
                    prefix = beforeKook.substring(0, firstColonIndex + 1).trim();
                }
                
                // 格式化：xiaofanbot: KOOK消息：用户： 用户名: 内容
                formatted = prefix + " KOOK消息：用户： " + userPart + ": " + contentPart;
            } else {
                // 如果没有冒号，直接使用原格式
                formatted = beforeKook.trim() + "KOOK消息：" + afterKook.trim();
            }
        } else if (message.contains("[QQ]")) {
            // 提取 [QQ] 后面的内容
            int qqIndex = message.indexOf("[QQ]");
            String beforeQq = message.substring(0, qqIndex);
            String afterQq = message.substring(qqIndex + 5); // "[QQ] " 长度为5
            
            // 查找冒号后的实际消息内容
            int colonIndex = afterQq.indexOf(":");
            if (colonIndex > 0) {
                String userPart = afterQq.substring(0, colonIndex).trim();
                String contentPart = afterQq.substring(colonIndex + 1).trim();
                
                // 提取前缀（xiaofanbot:）
                String prefix = "";
                int firstColonIndex = beforeQq.indexOf(":");
                if (firstColonIndex > 0) {
                    prefix = beforeQq.substring(0, firstColonIndex + 1).trim();
                }
                
                // 格式化：xiaofanbot: QQ消息：用户： 用户名: 内容
                formatted = prefix + " QQ消息：用户： " + userPart + ": " + contentPart;
            } else {
                // 如果没有冒号，直接使用原格式
                formatted = beforeQq.trim() + "QQ消息：" + afterQq.trim();
            }
        } else {
            // 如果没有来源标识，检查是否包含 "QQ消息" 或 "KOOK消息"
            if (message.contains("QQ消息")) {
                // 已经是正确格式，直接使用
                formatted = message;
            } else if (message.contains("KOOK消息")) {
                // 已经是正确格式，直接使用
                formatted = message;
            } else {
                // 默认作为QQ消息处理
                int colonIndex = message.indexOf(":");
                if (colonIndex > 0) {
                    String prefix = message.substring(0, colonIndex + 1);
                    String rest = message.substring(colonIndex + 1).trim();
                    formatted = prefix + " QQ消息：用户： " + rest;
                } else {
                    formatted = message;
                }
            }
        }
        
        return MESSAGE_PREFIX + formatted;
    }
    
    /**
     * 从API获取服务器消息
     * @return 消息内容，如果为null或异常返回null
     */
    private String fetchServerMessage() {
        try {
            Request request = new Request.Builder()
                    .url(SERVER_MESSAGE_API_URL)
                    .get()
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.debug("[服务器消息监控] API请求失败，HTTP状态码: {}", response.code());
                    return null;
                }
                
                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    logger.debug("[服务器消息监控] 响应体为空");
                    return null;
                }
                
                String responseText = responseBody.string();
                logger.debug("[服务器消息监控] 收到API响应: {}", responseText);
                
                // 解析JSON响应
                JSONObject jsonResponse = new JSONObject(responseText);
                
                // 检查是否有message字段
                if (!jsonResponse.has("message")) {
                    logger.debug("[服务器消息监控] 响应中缺少message字段");
                    return null;
                }
                
                // 获取message值
                Object messageObj = jsonResponse.get("message");
                
                // 如果是null，返回null
                if (messageObj == null || JSONObject.NULL.equals(messageObj)) {
                    return null;
                }
                
                // 转换为字符串
                String message = messageObj.toString();
                
                // 如果为空字符串，返回null
                if (message.trim().isEmpty()) {
                    return null;
                }
                
                return message;
                
            }
        } catch (IOException e) {
            logger.debug("[服务器消息监控] 网络请求异常: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            logger.debug("[服务器消息监控] 解析响应异常: {}", e.getMessage());
            return null;
        }
    }
}
