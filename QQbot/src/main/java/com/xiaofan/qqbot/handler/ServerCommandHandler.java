package com.xiaofan.qqbot.handler;

import com.xiaofan.qqbot.send.KookMessageSender;
import com.xiaofan.qqbot.send.QQMessageSender;
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
    private final QQMessageSender qqMessageSender;
    private final KookMessageSender kookMessageSender;
    
    /**
     * 构造函数（旧版本，兼容性）
     * @param messageSender 消息发送函数，接收群号和消息内容，返回是否发送成功
     */
    public ServerCommandHandler(BiFunction<Long, String, Boolean> messageSender) {
        this.messageSender = messageSender;
        this.qqMessageSender = null;
        this.kookMessageSender = null;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * 构造函数（新版本，支持双发送器）
     * @param qqMessageSender QQ消息发送器
     * @param kookMessageSender KOOK消息发送器
     */
    public ServerCommandHandler(QQMessageSender qqMessageSender, KookMessageSender kookMessageSender) {
        this.qqMessageSender = qqMessageSender;
        this.kookMessageSender = kookMessageSender;
        this.messageSender = null;
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
        
        // 提取命令内容（去除 /c 前缀）
        String content = extractCommandContent(messageText);
        if (content == null || content.trim().isEmpty()) {
            logger.warn("命令内容为空，群号: {}", groupId);
            String errorMsg = "发送失败：命令内容为空";
            if (qqMessageSender != null) {
                qqMessageSender.sendGroupMessage(groupId, errorMsg);
            } else if (messageSender != null) {
                messageSender.apply(groupId, errorMsg);
            }
            return;
        }
        
        // 发送到Minecraft服务器API（QQ来源）
        try {
            int statusCode = sendToServer(nickname, content, "qq");
            
            if (statusCode == 200) {
                // 成功
                String successMsg = "发送成功";
                if (qqMessageSender != null) {
                    qqMessageSender.sendGroupMessage(groupId, successMsg);
                } else if (messageSender != null) {
                    messageSender.apply(groupId, successMsg);
                }
                logger.info("命令发送成功，群号: {}, 用户: {}, 内容: {}", groupId, nickname, content);
            } else {
                // 失败
                String errorMsg = "发送失败（" + statusCode + "）";
                if (qqMessageSender != null) {
                    qqMessageSender.sendGroupMessage(groupId, errorMsg);
                } else if (messageSender != null) {
                    messageSender.apply(groupId, errorMsg);
                }
                logger.warn("命令发送失败，群号: {}, 用户: {}, HTTP状态码: {}", groupId, nickname, statusCode);
            }
        } catch (Exception e) {
            logger.error("发送命令到服务器时发生异常，群号: {}, 用户: {}", groupId, nickname, e);
            String errorMsg = "发送失败（" + e.getMessage() + "）";
            if (qqMessageSender != null) {
                qqMessageSender.sendGroupMessage(groupId, errorMsg);
            } else if (messageSender != null) {
                messageSender.apply(groupId, errorMsg);
            }
        }
    }
    
    /**
     * 提取命令内容（去除 /c 前缀）
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
     * @param nickname 用户昵称
     * @param content 消息内容
     * @param source 消息来源（"qq" 或 "kook"）
     * @return HTTP状态码
     * @throws IOException 网络异常
     */
    private int sendToServer(String nickname, String content, String source) throws IOException {
        // 构建请求体
        JSONObject requestBody = new JSONObject();
        requestBody.put("qq_id", nickname);
        
        // 只发送原始消息内容，不添加前缀（fabric模组会根据source字段构建完整格式）
        requestBody.put("message", content);
        requestBody.put("source", source); // 添加来源字段，fabric模组会根据此字段构建命令格式
        
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
    
    /**
     * 检查频道ID是否在目标列表中（KOOK版本）
     * 注意：KOOK使用字符串频道ID，这里暂时允许所有频道使用
     * @param channelId 频道ID
     * @return 是否允许（目前返回true，允许所有KOOK频道使用）
     */
    public boolean isTargetChannel(String channelId) {
        // KOOK消息暂时允许所有频道使用服务器命令
        // 如果需要限制，可以在这里添加频道ID列表
        return true;
    }
    
    /**
     * 处理 /c 命令（KOOK消息）
     * @param channelId 频道ID
     * @param userId 用户ID（字符串）
     * @param nickname 昵称
     * @param messageText 消息内容
     */
    public void handleCommandKook(String channelId, String userId, String nickname, String messageText) {
        logger.info("检测到 /c 命令（KOOK），频道: {}, 用户: {} ({})", channelId, nickname, userId);
        
        // 检查频道（目前允许所有KOOK频道）
        if (!isTargetChannel(channelId)) {
            logger.debug("频道 {} 不在目标列表中，忽略", channelId);
            return;
        }
        
        // 提取命令内容（去除 /c 前缀）
        String content = extractCommandContent(messageText);
        if (content == null || content.trim().isEmpty()) {
            logger.warn("命令内容为空，频道: {}", channelId);
            if (kookMessageSender != null) {
                kookMessageSender.sendChannelMessage(channelId, "发送失败：命令内容为空");
            }
            return;
        }
        
        // 发送到Minecraft服务器API（KOOK来源）
        try {
            int statusCode = sendToServer(nickname, content, "kook");
            
            if (statusCode == 200) {
                // 成功
                if (kookMessageSender != null) {
                    kookMessageSender.sendChannelMessage(channelId, "发送成功");
                }
                logger.info("命令发送成功（KOOK），频道: {}, 用户: {}, 内容: {}", channelId, nickname, content);
            } else {
                // 失败
                String errorMsg = "发送失败（" + statusCode + "）";
                if (kookMessageSender != null) {
                    kookMessageSender.sendChannelMessage(channelId, errorMsg);
                }
                logger.warn("命令发送失败（KOOK），频道: {}, 用户: {}, HTTP状态码: {}", channelId, nickname, statusCode);
            }
        } catch (Exception e) {
            logger.error("发送命令到服务器时发生异常（KOOK），频道: {}, 用户: {}", channelId, nickname, e);
            if (kookMessageSender != null) {
                kookMessageSender.sendChannelMessage(channelId, "发送失败（" + e.getMessage() + "）");
            }
        }
    }
}
