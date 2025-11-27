package com.xiaofan.qqbot.send;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * QQ消息发送器
 * 用于发送消息到QQ群
 */
public class QQMessageSender implements BiFunction<Long, String, Boolean> {
    private static final Logger logger = LoggerFactory.getLogger(QQMessageSender.class);
    
    private final OkHttpClient httpClient;
    private final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final String apiUrl;
    private final String token;
    
    public QQMessageSender(String apiUrl, String token) {
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
     * @param groupId 群号
     * @param message 消息内容
     * @return 是否发送成功
     */
    @Override
    public Boolean apply(Long groupId, String message) {
        return sendGroupMessage(groupId, message);
    }
    
    /**
     * 发送群消息
     * @param groupId 群号
     * @param message 消息内容
     * @return 是否发送成功
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
     * @param userId 用户QQ号
     * @param message 消息内容
     * @return 是否发送成功
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

