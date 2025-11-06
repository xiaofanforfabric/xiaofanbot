package com.xiaofan.qqbot;

import okhttp3.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * 服务器命令处理器
 * 处理 /c 命令，将消息发送到Minecraft服务器
 */
public class ServerCommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(ServerCommandHandler.class);
    
    // 目标群列表
    private static final List<Long> TARGET_GROUPS = Arrays.asList(
        1055829026L,
        1067452253L,
        721103774L
    );
    
    // Minecraft客户端API地址
    private static final String SERVER_COMMAND_API_URL = "http://127.0.0.1:2000/send_message_to_server";
    
    private final OkHttpClient httpClient;
    private final BiFunction<Long, String, Boolean> messageSender;
    
    /**
     * 构造函数
     * @param messageSender 消息发送函数，接收群号和消息内容，返回是否发送成功
     */
    public ServerCommandHandler(BiFunction<Long, String, Boolean> messageSender) {
        this.messageSender = messageSender;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * 检查消息是否匹配 /c 命令格式
     * @param messageText 消息内容
     * @return 是否匹配
     */
    public boolean shouldHandle(String messageText) {
        if (messageText == null || messageText.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = messageText.trim();
        // 检查是否以 /c 开头（允许有空格）
        return trimmed.startsWith("/c ") || trimmed.startsWith("/c");
    }
    
    /**
     * 检查群号是否在目标列表中
     * @param groupId 群号
     * @return 是否在目标列表中
     */
    public boolean isTargetGroup(long groupId) {
        return TARGET_GROUPS.contains(groupId);
    }
    
    /**
     * 处理 /c 命令
     * @param groupId 群号
     * @param userId QQ号
     * @param nickname 昵称
     * @param messageText 消息内容
     */
    public void handleCommand(long groupId, long userId, String nickname, String messageText) {
        logger.info("检测到 /c 命令，群号: {}, 用户: {} ({})", groupId, nickname, userId);
        
        // 检查群号
        if (!isTargetGroup(groupId)) {
            logger.debug("群号 {} 不在目标列表中，忽略", groupId);
            return;
        }
        
        // 提取命令内容（去掉 /c 前缀）
        String content = extractCommandContent(messageText);
        if (content == null || content.trim().isEmpty()) {
            logger.warn("命令内容为空，群号: {}", groupId);
            messageSender.apply(groupId, "发送失败：命令内容为空");
            return;
        }
        
        // 发送到Minecraft服务器API
        try {
            int statusCode = sendToServer(nickname, content);
            
            if (statusCode == 200) {
                // 成功
                messageSender.apply(groupId, "发送成功");
                logger.info("命令发送成功，群号: {}, 用户: {}, 内容: {}", groupId, nickname, content);
            } else {
                // 失败
                String errorMsg = "发送失败（" + statusCode + "）";
                messageSender.apply(groupId, errorMsg);
                logger.warn("命令发送失败，群号: {}, 用户: {}, HTTP状态码: {}", groupId, nickname, statusCode);
            }
        } catch (Exception e) {
            logger.error("发送命令到服务器时发生异常，群号: {}, 用户: {}", groupId, nickname, e);
            messageSender.apply(groupId, "发送失败（" + e.getMessage() + "）");
        }
    }
    
    /**
     * 提取命令内容（去掉 /c 前缀）
     * @param messageText 原始消息
     * @return 命令内容
     */
    private String extractCommandContent(String messageText) {
        if (messageText == null) {
            return null;
        }
        
        String trimmed = messageText.trim();
        
        // 处理 /c 或 /c 空格的情况
        if (trimmed.startsWith("/c ")) {
            return trimmed.substring(3).trim();
        } else if (trimmed.startsWith("/c")) {
            // 如果紧跟着非空格字符，可能是 /c 后面直接跟内容
            if (trimmed.length() > 2) {
                return trimmed.substring(2).trim();
            }
            return "";
        }
        
        return null;
    }
    
    /**
     * 发送命令到Minecraft服务器
     * @param nickname 用户昵称（作为qq_id）
     * @param content 消息内容
     * @return HTTP状态码
     * @throws IOException 网络异常
     */
    private int sendToServer(String nickname, String content) throws IOException {
        // 构建请求体
        JSONObject requestBody = new JSONObject();
        requestBody.put("qq_id", nickname);
        requestBody.put("message", content);
        
        RequestBody body = RequestBody.create(
            requestBody.toString(),
            MediaType.get("application/json; charset=utf-8")
        );
        
        Request request = new Request.Builder()
                .url(SERVER_COMMAND_API_URL)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            int statusCode = response.code();
            
            if (response.body() != null) {
                String responseText = response.body().string();
                logger.debug("API响应: HTTP {}, 内容: {}", statusCode, responseText);
            }
            
            return statusCode;
        }
    }
}

