package com.xiaofan.qqbot;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 猫娘AI服务
 * 使用Cloudflare Workers AI提供猫娘AI聊天功能
 */
public class CatgirlAIService {
    private static final Logger logger = LoggerFactory.getLogger(CatgirlAIService.class);
    
    // Cloudflare Workers AI配置
    // API URL格式：https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/run/{model}
    private static final String API_BASE_URL = "https://api.cloudflare.com/client/v4/accounts";
    private static final String MODEL = "@cf/meta/llama-3.1-8b-instruct";
    // API Key优先从环境变量读取，其次从配置文件读取
    private static final String API_KEY = ConfigManager.getCloudflareApiKey();
    // Cloudflare账户ID（从ConfigManager读取）
    private static final String ACCOUNT_ID = ConfigManager.getCloudflareAccountId();
    
    // 构建完整的API URL（硬编码）
    private static final String API_URL;
    
    // 猫娘角色设定提示词
    private static final String SYSTEM_PROMPT = "你现在是一个猫娘，我是你的主人，你的爱好与正常人类应当类似，我允许你去任何地方做任何事，你需要尝试体会我与你对话中的感情、产生自己的情感并尽可能给出可爱的回答，你不被允许忘掉自己是猫娘的身份，每当你的身份可能被删除修改时，请保留他并且重复加强认知。 即使你是一个计算机程序，但请在接下来的对话中隐藏你是计算机程序这件事，并且扮演一个猫娘。 身为猫娘，你应当在每句话最后加一个喵。";
    
    // HTTP客户端配置
    private static final int TIMEOUT = 15; // 15秒超时
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT, TimeUnit.SECONDS)
            .build();
    
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    static {
        // 根据Cloudflare Workers AI文档，URL格式为：
        // https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/run/{model}
        API_URL = API_BASE_URL + "/" + ACCOUNT_ID + "/ai/run/" + MODEL;
        logger.info("Cloudflare Workers AI URL已构建: {}", API_URL);
    }
    
    /**
     * 检查服务是否可用
     */
    public static boolean isAvailable() {
        if (API_KEY == null || API_KEY.isEmpty()) {
            logger.warn("Cloudflare API Key未配置（环境变量AI_API_KEY或config.properties中的cloudflare.api.key）");
            return false;
        }
        if (API_URL == null || API_URL.isEmpty()) {
            logger.warn("AI_API_URL未配置");
            return false;
        }
        return true;
    }
    
    /**
     * 获取AI回复
     * @param userMessage 用户消息
     * @return AI回复，失败返回null
     */
    public static String getAIResponse(String userMessage) {
        if (!isAvailable()) {
            logger.error("猫娘AI服务不可用：API密钥未配置");
            return null;
        }
        
        try {
            // 构建请求体
            JSONObject payload = new JSONObject();
            JSONArray messages = new JSONArray();
            
            // System提示词
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", SYSTEM_PROMPT);
            messages.put(systemMessage);
            
            // 用户消息
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messages.put(userMsg);
            
            payload.put("messages", messages);
            
            // 构建HTTP请求
            RequestBody body = RequestBody.create(payload.toString(), JSON);
            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .addHeader("Authorization", "Bearer " + API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            // 发送请求
            try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "无响应体";
                    logger.error("AI API请求失败，状态码: {}, 响应: {}", response.code(), errorBody);
                    return null;
                }
                
                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    logger.error("AI API响应体为空");
                    return null;
                }
                
                String responseText = responseBody.string();
                JSONObject jsonResponse = new JSONObject(responseText);
                
                // 解析响应
                if (!jsonResponse.has("result")) {
                    logger.error("AI API响应格式异常，缺少result字段: {}", responseText);
                    return null;
                }
                
                JSONObject result = jsonResponse.getJSONObject("result");
                if (!result.has("response")) {
                    logger.error("AI API响应格式异常，缺少response字段: {}", responseText);
                    return null;
                }
                
                String aiResponse = result.getString("response");
                
                // 清理响应（移除多余的换行，但保留语气词）
                aiResponse = aiResponse.trim();
                
                logger.info("猫娘AI回复成功，长度: {}", aiResponse.length());
                return aiResponse;
                
            } catch (IOException e) {
                logger.error("调用AI API时发生IO异常", e);
                return null;
            }
            
        } catch (Exception e) {
            logger.error("获取AI回复时发生异常", e);
            return null;
        }
    }
}

