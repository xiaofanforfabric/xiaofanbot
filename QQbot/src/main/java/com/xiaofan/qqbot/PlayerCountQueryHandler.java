package com.xiaofan.qqbot;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * 人数查询处理器
 * 检测"人数查询"关键词，向Minecraft客户端API查询在线玩家信息并发送到QQ群
 */
public class PlayerCountQueryHandler {
    private static final Logger logger = LoggerFactory.getLogger(PlayerCountQueryHandler.class);
    
    // Minecraft客户端API地址
    private static final String MINECRAFT_API_URL = "http://127.0.0.1:2000/need_server_info";
    private static final String TRIGGER_KEYWORD = "人数查询";
    
    private final OkHttpClient httpClient;
    private final BiFunction<Long, String, Boolean> messageSender;
    
    /**
     * 构造函数
     * @param messageSender 消息发送函数，接收群号和消息内容，返回是否发送成功
     */
    public PlayerCountQueryHandler(BiFunction<Long, String, Boolean> messageSender) {
        this.messageSender = messageSender;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * 检查消息是否完全匹配触发关键词（去除首尾空格后精确匹配）
     */
    public boolean shouldHandle(String messageText) {
        if (messageText == null) {
            return false;
        }
        return messageText.trim().equals(TRIGGER_KEYWORD);
    }
    
    /**
     * 处理人数查询请求
     */
    public void handleQuery(long groupId, String messageText) {
        if (!shouldHandle(messageText)) {
            return;
        }
        
        logger.info("检测到人数查询请求，群号: {}", groupId);
        
        try {
            // 查询Minecraft服务器在线玩家信息
            PlayerCountInfo playerInfo = queryPlayerCount();
            
            if (playerInfo == null) {
                messageSender.apply(groupId, "查询失败：无法连接到Minecraft客户端API");
                return;
            }
            
            // 格式化消息
            String formattedMessage = formatPlayerCountMessage(playerInfo);
            
            // 发送到QQ群
            boolean success = messageSender.apply(groupId, formattedMessage);
            
            if (success) {
                logger.info("人数查询消息已成功发送到群: {}", groupId);
            } else {
                logger.error("人数查询消息发送失败，群号: {}", groupId);
            }
            
        } catch (Exception e) {
            logger.error("处理人数查询时发生错误", e);
            messageSender.apply(groupId, "查询失败：" + e.getMessage());
        }
    }
    
    /**
     * 查询Minecraft服务器在线玩家数量
     */
    private PlayerCountInfo queryPlayerCount() {
        try {
            Request request = new Request.Builder()
                    .url(MINECRAFT_API_URL)
                    .get()
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.error("查询玩家数量失败，HTTP状态码: {}", response.code());
                    ResponseBody errorBody = response.body();
                    if (errorBody != null) {
                        logger.error("错误响应: {}", errorBody.string());
                    }
                    return null;
                }
                
                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    logger.error("响应体为空");
                    return null;
                }
                
                String responseText = responseBody.string();
                logger.debug("收到API响应: {}", responseText);
                
                // 解析JSON响应
                JSONObject jsonResponse = new JSONObject(responseText);
                
                // 检查是否有错误
                if (jsonResponse.has("error")) {
                    String error = jsonResponse.getString("error");
                    logger.error("API返回错误: {}", error);
                    return null;
                }
                
                // 解析玩家信息
                int count = jsonResponse.optInt("count", 0);
                JSONArray playersArray = jsonResponse.optJSONArray("online_players");
                
                PlayerCountInfo info = new PlayerCountInfo();
                info.count = count;
                
                if (playersArray != null) {
                    for (int i = 0; i < playersArray.length(); i++) {
                        JSONObject playerObj = playersArray.getJSONObject(i);
                        String username = playerObj.optString("username", "未知");
                        int latency = playerObj.optInt("latency", 0);
                        info.addPlayer(username, latency);
                    }
                }
                
                return info;
            }
        } catch (Exception e) {
            logger.error("查询玩家数量时发生异常", e);
            return null;
        }
    }
    
    /**
     * 格式化玩家数量消息
     */
    private String formatPlayerCountMessage(PlayerCountInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append("邦国崛起在线人数：").append(info.count).append("\n");
        
        PlayerInfo[] players = info.getPlayers();
        if (players.length == 0) {
            sb.append("当前无在线玩家");
        } else {
            for (int i = 0; i < players.length; i++) {
                PlayerInfo player = players[i];
                sb.append("在线玩家");
                if (i > 0) {
                    sb.append(i + 1);
                }
                sb.append("：（").append(player.username).append("）延迟：（").append(player.latency).append("）\n");
            }
            // 移除最后一个换行符
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
                sb.setLength(sb.length() - 1);
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 玩家信息数据类
     */
    private static class PlayerInfo {
        String username;
        int latency;
        
        PlayerInfo(String username, int latency) {
            this.username = username;
            this.latency = latency;
        }
    }
    
    /**
     * 玩家数量信息数据类
     */
    private static class PlayerCountInfo {
        int count = 0;
        PlayerInfo[] players = new PlayerInfo[0];
        int playerIndex = 0;
        
        void addPlayer(String username, int latency) {
            if (players.length == 0) {
                players = new PlayerInfo[10]; // 初始容量
            }
            if (playerIndex >= players.length) {
                // 扩容
                PlayerInfo[] newPlayers = new PlayerInfo[players.length * 2];
                System.arraycopy(players, 0, newPlayers, 0, players.length);
                players = newPlayers;
            }
            players[playerIndex++] = new PlayerInfo(username, latency);
        }
        
        // 获取实际玩家数组（去除null）
        PlayerInfo[] getPlayers() {
            if (playerIndex == 0) {
                return new PlayerInfo[0];
            }
            PlayerInfo[] result = new PlayerInfo[playerIndex];
            System.arraycopy(players, 0, result, 0, playerIndex);
            return result;
        }
    }
}

