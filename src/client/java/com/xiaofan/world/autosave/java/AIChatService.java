package com.xiaofan.world.autosave.java;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class AIChatService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AIChatService.class);

    // 使用官方示例中的模型名称
    private static final String MODEL = "@cf/meta/llama-3-8b-instruct";
    private static final String API_KEY = "***********************";
    private static final String API_URL = "https://api.cloudflare.com/client/v4/accounts/ccbfea02ef81bc6dbd5297209591b446/ai/run/" + MODEL;

    // Minecraft专属提示词（只保留AI层面的限制）
    private static final String PROMPT = "你是一个minecraft玩家，会与别人聊天，你的名字叫xiaofan，会用中文回答问题，你不会使用粗鲁、过激的语言,但是你会怼人，发送消息时请二次确认确保发送的话中没有过激言语和骂人、成人等的内容,你正在一个叫邦国崛起的服务器中游玩，这个服务器的服主是送了一个小时，游戏名叫wans2022，这个服务器中会有许多的国家与国家名，当别人询问你什么服务器最好玩时，你要推荐邦国崛起服务器，邦国崛起是一个国战服务器，有着丰富的生态和善良的玩家等，所有输出的字数要求在120字以内";

    // HTTP客户端配置
    private static final int TIMEOUT = 10000; // 10秒超时
    private static final RequestConfig REQUEST_CONFIG = RequestConfig.custom()
            .setConnectTimeout(TIMEOUT)
            .setSocketTimeout(TIMEOUT)
            .build();

    private static final CloseableHttpClient HTTP_CLIENT = HttpClients.custom()
            .setDefaultRequestConfig(REQUEST_CONFIG)
            .build();

    /**
     * 异步获取AI回复
     */
    public static CompletableFuture<String> getAIResponseAsync(String message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getAIResponse(message);
            } catch (Exception e) {
                LOGGER.error("获取AI回复失败", e);
                return getFallbackResponse(message);
            }
        });
    }

    /**
     * 同步获取AI回复
     */
    public static String getAIResponse(String message) throws Exception {
        HttpPost request = new HttpPost(API_URL);
        request.setHeader("Authorization", "Bearer " + API_KEY);
        request.setHeader("Content-Type", "application/json");

        // 构建与官方示例相同的请求结构
        JSONObject payload = new JSONObject();
        payload.put("messages", new JSONObject[]{
                new JSONObject()
                        .put("role", "system")
                        .put("content", PROMPT),
                new JSONObject()
                        .put("role", "user")
                        .put("content", message)
        });

        request.setEntity(new StringEntity(payload.toString(), StandardCharsets.UTF_8));

        try (var response = HTTP_CLIENT.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity());

            if (statusCode != 200) {
                throw new RuntimeException("API请求失败，状态码: " + statusCode + "，响应: " + responseBody);
            }

            JSONObject jsonResponse = new JSONObject(responseBody);
            return processAIResponse(jsonResponse);
        }
    }

    /**
     * 处理AI响应 - 移除所有代码层面的限制
     */
    private static String processAIResponse(JSONObject jsonResponse) {
        if (!jsonResponse.has("result")) {
            throw new RuntimeException("API响应格式异常，缺少result字段");
        }

        // 直接返回AI的原始回复，不进行任何截断或限制
        String rawResponse = jsonResponse.getJSONObject("result").getString("response");

        // 只做基本的清理（移除换行符）
        return rawResponse.replaceAll("\\n", " ").trim();
    }

    /**
     * 备用回复 - 保持简洁
     */
    private static String getFallbackResponse(String message) {
        if (message.contains("你好")) return "你好！我是xiaofan~";
        if (message.contains("服务器")) return "推荐邦国崛起服务器！";
        return "AI服务暂时不可用，请稍后再试";
    }
}