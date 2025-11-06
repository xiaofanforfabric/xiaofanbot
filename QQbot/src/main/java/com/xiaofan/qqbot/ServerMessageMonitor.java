package com.xiaofan.qqbot;

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
    
    // 消息前缀
    private static final String MESSAGE_PREFIX = "邦国崛起服务器消息：";
    
    private final OkHttpClient httpClient;
    private final BiFunction<Long, String, Boolean> messageSender;
    private ScheduledExecutorService scheduler;
    private boolean isRunning = false;
    
    /**
     * 构造函数
     * @param messageSender 消息发送函数，接收群号和消息内容，返回是否发送成功
     */
    public ServerMessageMonitor(BiFunction<Long, String, Boolean> messageSender) {
        this.messageSender = messageSender;
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
        logger.info("[服务器消息监控] 目标群: {}", TARGET_GROUPS);
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
                // 有有效消息，发送到所有目标群
                String formattedMessage = MESSAGE_PREFIX + message;
                
                for (Long groupId : TARGET_GROUPS) {
                    try {
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

