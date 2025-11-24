package com.xiaofan.world.autosave.message;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

public class mes {
    private static final int HTTP_PORT = 2000;
    private static HttpServer httpServer;
    private static boolean isRunning = false;
    
    // 聊天消息队列（线程安全）
    private static final ConcurrentLinkedQueue<String> chatMessageQueue = new ConcurrentLinkedQueue<>();

    /**
     * 初始化HTTP服务器和消息监听
     */
    public static void initialize() {
        if (isRunning) {
            return;
        }

        // 注册消息监听器
        registerMessageListeners();

        try {
            httpServer = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
            httpServer.createContext("/need_server_info", new ServerInfoHandler());
            httpServer.createContext("/get_server_last_message", new LastMessageHandler());
            httpServer.createContext("/send_message_to_server", new SendMessageHandler());
            httpServer.setExecutor(Executors.newCachedThreadPool());
            httpServer.start();
            isRunning = true;
            
            System.out.println("[服务器信息API] HTTP服务器已启动 (端口: " + HTTP_PORT + ")");
            
        } catch (IOException e) {
            System.err.println("[服务器信息API] 启动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 注册消息监听器，仅监听玩家聊天消息（不包括系统消息）
     */
    private static void registerMessageListeners() {
        // 监听GAME事件（自定义消息系统可能通过GAME事件发送）
        // 需要过滤掉系统消息（如"加入了游戏"、"离开服务器"等）
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String messageText = message.getString();
            if (messageText != null && !messageText.trim().isEmpty()) {
                System.out.println("[服务器信息API] [GAME事件] 收到消息: " + messageText + " (overlay: " + overlay + ")");
                
                // 过滤系统消息
                if (isSystemMessage(messageText)) {
                    System.out.println("[服务器信息API] [GAME事件] 过滤系统消息: " + messageText);
                    return;
                }
                
                // 去掉 [System] [CHAT] 前缀（如果存在）
                String cleanMessage = removeSystemPrefix(messageText);
                
                // 检查消息是否匹配自定义格式（必须有方括号前缀）
                if (!hasBracketFormat(cleanMessage)) {
                    System.out.println("[服务器信息API] [GAME事件] 消息格式不匹配（无方括号），跳过: " + cleanMessage);
                    return;
                }
                
                // 解析自定义消息格式，提取实际的聊天内容
                String parsedMessage = parseCustomMessageFormat(cleanMessage);
                
                // 只有成功解析出内容才加入队列
                if (parsedMessage != null && !parsedMessage.trim().isEmpty() && !parsedMessage.equals(cleanMessage)) {
                    chatMessageQueue.offer(parsedMessage);
                    System.out.println("[服务器信息API] [GAME事件] 解析后消息已加入队列: " + parsedMessage);
                } else {
                    System.out.println("[服务器信息API] [GAME事件] 解析失败或格式不匹配，未加入队列");
                }
            }
        });
        
        // 监听玩家聊天消息（使用CHAT事件）
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            String messageText = message.getString();
            if (messageText != null && !messageText.trim().isEmpty()) {
                System.out.println("[服务器信息API] [CHAT事件] 收到原始聊天消息: " + messageText);
                
                // 过滤系统消息
                if (isSystemMessage(messageText)) {
                    System.out.println("[服务器信息API] [CHAT事件] 过滤系统消息: " + messageText);
                    return;
                }
                
                // 去掉 [System] [CHAT] 前缀（如果存在）
                String cleanMessage = removeSystemPrefix(messageText);
                
                // 检查消息是否匹配自定义格式（必须有方括号前缀）
                if (!hasBracketFormat(cleanMessage)) {
                    System.out.println("[服务器信息API] [CHAT事件] 消息格式不匹配（无方括号），跳过: " + cleanMessage);
                    return;
                }
                
                // 解析自定义消息格式，提取实际的聊天内容
                String parsedMessage = parseCustomMessageFormat(cleanMessage);
                
                // 只有成功解析出内容才加入队列
                if (parsedMessage != null && !parsedMessage.trim().isEmpty() && !parsedMessage.equals(cleanMessage)) {
                    chatMessageQueue.offer(parsedMessage);
                    System.out.println("[服务器信息API] [CHAT事件] 解析后消息已加入队列: " + parsedMessage);
                } else {
                    System.out.println("[服务器信息API] [CHAT事件] 解析失败或格式不匹配，未加入队列");
                }
            }
        });
        
        // 同时监听ALLOW_CHAT事件（备用）
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            String messageText = message.getString();
            if (messageText != null && !messageText.trim().isEmpty()) {
                System.out.println("[服务器信息API] [ALLOW_CHAT事件] 收到原始聊天消息: " + messageText);
                
                // 过滤系统消息
                if (isSystemMessage(messageText)) {
                    System.out.println("[服务器信息API] [ALLOW_CHAT事件] 过滤系统消息: " + messageText);
                    return true;
                }
                
                // 去掉 [System] [CHAT] 前缀（如果存在）
                String cleanMessage = removeSystemPrefix(messageText);
                
                // 检查消息是否匹配自定义格式（必须有方括号前缀）
                if (!hasBracketFormat(cleanMessage)) {
                    System.out.println("[服务器信息API] [ALLOW_CHAT事件] 消息格式不匹配（无方括号），跳过: " + cleanMessage);
                    return true;
                }
                
                // 解析自定义消息格式，提取实际的聊天内容
                String parsedMessage = parseCustomMessageFormat(cleanMessage);
                
                // 只有成功解析出内容才加入队列
                if (parsedMessage != null && !parsedMessage.trim().isEmpty() && !parsedMessage.equals(cleanMessage)) {
                    chatMessageQueue.offer(parsedMessage);
                    System.out.println("[服务器信息API] [ALLOW_CHAT事件] 解析后消息已加入队列: " + parsedMessage);
                } else {
                    System.out.println("[服务器信息API] [ALLOW_CHAT事件] 解析失败或格式不匹配，未加入队列");
                }
            }
            return true; // 允许消息显示
        });
        
        System.out.println("[服务器信息API] 消息监听器已注册（监听GAME、CHAT和ALLOW_CHAT事件）");
    }
    
    /**
     * 判断是否是系统消息（需要过滤掉的消息）
     * @param message 消息内容
     * @return true表示是系统消息，需要过滤
     */
    private static boolean isSystemMessage(String message) {
        if (message == null) {
            return true;
        }
        
        String lower = message.toLowerCase();
        
        // 过滤系统提示消息
        return lower.contains("加入了游戏") ||
               lower.contains("离开服务器") ||
               lower.contains("进入了服务器") ||
               lower.contains("退出了服务器") ||
               lower.contains("joined the game") ||
               lower.contains("left the game");
    }
    
    /**
     * 判断消息是否包含方括号格式（用于识别自定义消息格式）
     * @param message 消息内容
     * @return true表示消息中包含至少一个方括号块，可能是自定义格式
     */
    private static boolean hasBracketFormat(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = message.trim();
        // 检查是否包含至少一个完整的方括号块 [xxx]
        Pattern bracketPattern = Pattern.compile("\\[[^\\]]+\\]");
        return bracketPattern.matcher(trimmed).find();
    }
    
    /**
     * 去掉消息中的 [System] [CHAT] 前缀
     * @param message 原始消息
     * @return 去掉前缀后的消息
     */
    private static String removeSystemPrefix(String message) {
        if (message == null) {
            return null;
        }
        
        // 去掉 [System] [CHAT] 前缀
        String cleaned = message;
        
        // 匹配 [System] [CHAT] 前缀并去掉
        Pattern pattern = Pattern.compile("^\\s*\\[System\\]\\s*\\[CHAT\\]\\s*(.+)$", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(cleaned);
        if (matcher.matches()) {
            cleaned = matcher.group(1).trim();
            System.out.println("[服务器信息API] 去掉 [System] [CHAT] 前缀: " + message + " -> " + cleaned);
        }
        
        // 匹配 [System] 前缀并去掉
        pattern = Pattern.compile("^\\s*\\[System\\]\\s*(.+)$", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(cleaned);
        if (matcher.matches()) {
            cleaned = matcher.group(1).trim();
            System.out.println("[服务器信息API] 去掉 [System] 前缀: " + message + " -> " + cleaned);
        }
        
        return cleaned;
    }
    
    /**
     * 解析自定义消息格式
     * 支持三种格式：
     * 1. [xxx] [xxx] [xxx] 实际消息内容（三个方括号）
     * 2. [xxx] [xxx] 实际消息内容（两个方括号）
     * 3. [xxx] 实际消息内容（一个方括号）
     * 
     * 格式示例: 
     * - [塞勒涅盟约] [雅典维亚城邦] [幸运戴师OVO] xiaofan: 114514
     * - [信息1] [信息2] 消息内容
     * - [信息] 消息内容
     * 
     * @param rawMessage 原始消息
     * @return 解析后的聊天内容（去掉方括号前缀后的实际消息），如果解析失败返回null
     */
    private static String parseCustomMessageFormat(String rawMessage) {
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            System.out.println("[服务器信息API] 解析消息: 输入为空");
            return null;
        }
        
        String trimmed = rawMessage.trim();
        System.out.println("[服务器信息API] 开始解析消息: " + trimmed);
        
        // 格式1: [xxx] [xxx] [xxx] 实际消息内容（三个方括号）
        // 示例: [塞勒涅盟约] [雅典维亚城邦] [幸运戴师OVO] xiaofan: 114514
        Pattern pattern = Pattern.compile(
            "^\\s*\\[([^\\]]+)\\]\\s*\\[([^\\]]+)\\]\\s*\\[([^\\]]+)\\]\\s*(.+)$"
        );
        
        Matcher matcher = pattern.matcher(trimmed);
        if (matcher.matches()) {
            String info1 = matcher.group(1);  // 第一个方括号内容
            String info2 = matcher.group(2);  // 第二个方括号内容
            String info3 = matcher.group(3);  // 第三个方括号内容
            String chatContent = matcher.group(4).trim(); // 实际的聊天内容
            
            System.out.println("[服务器信息API] ✓ 匹配三括号格式 - [" + info1 + "] [" + info2 + "] [" + info3 + "] -> " + chatContent);
            
            return chatContent;
        }
        
        // 格式2: [xxx] [xxx] 实际消息内容（两个方括号）
        pattern = Pattern.compile(
            "^\\s*\\[([^\\]]+)\\]\\s*\\[([^\\]]+)\\]\\s*(.+)$"
        );
        matcher = pattern.matcher(trimmed);
        if (matcher.matches()) {
            String info1 = matcher.group(1);
            String info2 = matcher.group(2);
            String chatContent = matcher.group(3).trim();
            
            System.out.println("[服务器信息API] ✓ 匹配两括号格式 - [" + info1 + "] [" + info2 + "] -> " + chatContent);
            
            return chatContent;
        }
        
        // 格式3: [xxx] 实际消息内容（一个方括号）
        pattern = Pattern.compile(
            "^\\s*\\[([^\\]]+)\\]\\s*(.+)$"
        );
        matcher = pattern.matcher(trimmed);
        if (matcher.matches()) {
            String info = matcher.group(1);
            String chatContent = matcher.group(2).trim();
            
            System.out.println("[服务器信息API] ✓ 匹配单括号格式 - [" + info + "] -> " + chatContent);
            
            return chatContent;
        }
        
        // 如果都不匹配，返回null（不加入队列）
        System.out.println("[服务器信息API] ✗ 消息格式不匹配任何模式，返回null: " + trimmed);
        return null;
    }

    /**
     * 停止HTTP服务器
     */
    public static void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            isRunning = false;
            System.out.println("[服务器信息API] HTTP服务器已停止");
        }
    }

    /**
     * 服务器信息处理器
     */
    private static class ServerInfoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 只处理GET请求
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {
                MinecraftClient client = MinecraftClient.getInstance();
                
                // 检查客户端是否连接到服务器
                boolean isConnected = client != null 
                        && client.getNetworkHandler() != null 
                        && client.player != null;

                if (!isConnected) {
                    // 未连接状态，返回502
                    sendError(exchange, 502, "Fabric client is not connected to server");
                    return;
                }

                // 获取在线玩家详细信息
                List<PlayerInfo> playerInfoList = getOnlinePlayers(client);
                
                // 构建JSON响应
                StringBuilder jsonBuilder = new StringBuilder();
                jsonBuilder.append("{\"online_players\":[");
                
                for (int i = 0; i < playerInfoList.size(); i++) {
                    PlayerInfo info = playerInfoList.get(i);
                    if (i > 0) {
                        jsonBuilder.append(",");
                    }
                    jsonBuilder.append("{");
                    jsonBuilder.append("\"username\":\"").append(escapeJson(info.username)).append("\",");
                    jsonBuilder.append("\"latency\":").append(info.latency);
                    jsonBuilder.append("}");
                }
                
                jsonBuilder.append("],\"count\":").append(playerInfoList.size()).append("}");
                
                String jsonResponse = jsonBuilder.toString();

                // 发送响应
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(200, jsonResponse.getBytes(StandardCharsets.UTF_8).length);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(jsonResponse.getBytes(StandardCharsets.UTF_8));
                }

            } catch (Exception e) {
                System.err.println("[服务器信息API] 处理请求时出错: " + e.getMessage());
                e.printStackTrace();
                sendError(exchange, 500, "Internal server error");
            }
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
         * 获取在线玩家详细信息（包括用户名和延迟）
         */
        private List<PlayerInfo> getOnlinePlayers(MinecraftClient client) {
            List<PlayerInfo> playerInfoList = new ArrayList<>();
            
            try {
                if (client.getNetworkHandler() != null) {
                    var playerList = client.getNetworkHandler().getPlayerList();
                    if (playerList != null) {
                        // 遍历所有玩家条目（getPlayerList()返回Collection<PlayerListEntry>）
                        for (PlayerListEntry entry : playerList) {
                            if (entry != null) {
                                String username = "未知";
                                int latency = 0;
                                
                                try {
                                    // 获取玩家名称
                                    if (entry.getProfile() != null && entry.getProfile().getName() != null) {
                                        username = entry.getProfile().getName();
                                    } else if (entry.getDisplayName() != null) {
                                        // 如果Profile名称为空，尝试从DisplayName获取
                                        username = entry.getDisplayName().getString();
                                    }
                                    
                                    // 获取延迟（以毫秒为单位）
                                    latency = entry.getLatency();
                                    
                                } catch (Exception e) {
                                    System.err.println("[服务器信息API] 获取玩家信息时出错: " + e.getMessage());
                                }
                                
                                playerInfoList.add(new PlayerInfo(username, latency));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[服务器信息API] 获取玩家列表时出错: " + e.getMessage());
                e.printStackTrace();
            }
            
            return playerInfoList;
        }

        /**
         * 发送错误响应
         */
        private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
            String errorResponse = String.format("{\"error\":\"%s\"}", escapeJson(message));
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(statusCode, errorResponse.getBytes(StandardCharsets.UTF_8).length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(errorResponse.getBytes(StandardCharsets.UTF_8));
            }
        }

        /**
         * 转义JSON字符串
         */
        private String escapeJson(String str) {
            if (str == null) {
                return "";
            }
            return str.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }
    }
    
    /**
     * 最后一条消息处理器
     * 返回聊天框的最后一条消息，每次返回后清空，确保不会重复获取
     */
    private static class LastMessageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 只处理GET请求
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {
                // 从队列中取出并移除一条消息（FIFO）
                String message = chatMessageQueue.poll();
                
                String response;
                if (message != null) {
                    // 返回消息内容
                    response = String.format("{\"message\":\"%s\"}", escapeJson(message));
                    System.out.println("[服务器信息API] 返回消息: " + message);
                } else {
                    // 队列为空，返回null
                    response = "{\"message\":null}";
                    System.out.println("[服务器信息API] 队列为空，返回null");
                }

                // 发送响应
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }

            } catch (Exception e) {
                System.err.println("[服务器信息API] 处理请求时出错: " + e.getMessage());
                e.printStackTrace();
                sendError(exchange, 500, "Internal server error");
            }
        }
        
        /**
         * 发送错误响应
         */
        private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
            String errorResponse = String.format("{\"error\":\"%s\"}", escapeJson(message));
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(statusCode, errorResponse.getBytes(StandardCharsets.UTF_8).length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(errorResponse.getBytes(StandardCharsets.UTF_8));
            }
        }
        
        /**
         * 转义JSON字符串
         */
        private String escapeJson(String str) {
            if (str == null) {
                return "";
            }
            return str.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }
    }
    
    /**
     * 发送消息到服务器处理器
     * 接收POST请求，将消息发送到Minecraft服务器
     */
    private static class SendMessageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 只处理POST请求
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {
                // 读取请求体
                InputStream requestBody = exchange.getRequestBody();
                String requestText = new String(requestBody.readAllBytes(), StandardCharsets.UTF_8);
                
                System.out.println("[服务器信息API] 收到发送消息请求: " + requestText);
                
                // 解析JSON
                JSONObject requestJson = new JSONObject(requestText);
                
                String qqId = requestJson.optString("qq_id", "");
                String message = requestJson.optString("message", "");
                String source = requestJson.optString("source", "qq"); // 默认为qq，支持kook
                
                // 验证参数
                if (message == null || message.trim().isEmpty()) {
                    sendError(exchange, 400, "message字段不能为空");
                    return;
                }
                
                // 发送命令到Minecraft服务器
                boolean success = sendCommandToServer(qqId, message, source);
                
                if (success) {
                    String response = String.format("{\"status\":\"success\",\"message\":\"命令已发送\",\"qq_id\":\"%s\",\"source\":\"%s\"}", escapeJson(qqId), escapeJson(source));
                    sendResponse(exchange, 200, response);
                    String sourceLabel = "kook".equalsIgnoreCase(source) ? "KOOK" : "QQ";
                    System.out.println("[服务器信息API] 命令已发送: /c " + sourceLabel + "消息：用户：" + qqId + ": " + message + " (" + sourceLabel + ": " + qqId + ")");
                } else {
                    String response = "{\"status\":\"error\",\"message\":\"客户端未连接到服务器\"}";
                    sendResponse(exchange, 502, response);
                    System.out.println("[服务器信息API] 发送失败: 客户端未连接");
                }

            } catch (Exception e) {
                System.err.println("[服务器信息API] 处理请求时出错: " + e.getMessage());
                e.printStackTrace();
                sendError(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }
        
        /**
         * 发送命令到Minecraft服务器
         * @param qqId 用户昵称（作为qq_id）
         * @param message 消息内容
         * @param source 消息来源（"qq" 或 "kook"）
         * @return 是否发送成功
         */
        private boolean sendCommandToServer(String qqId, String message, String source) {
            MinecraftClient client = MinecraftClient.getInstance();
            
            // 检查客户端是否连接到服务器
            if (client == null || client.getNetworkHandler() == null || client.player == null) {
                return false;
            }
            
            try {
                // 根据来源构建不同的命令格式
                String command;
                if ("kook".equalsIgnoreCase(source)) {
                    // KOOK消息格式：/c KOOK消息：用户：用户名: 消息内容
                    command = "c KOOK消息：用户：" + qqId + ": " + message;
                } else {
                    // QQ消息格式：/c QQ消息：用户：用户名: 消息内容
                    command = "c QQ消息：用户：" + qqId + ": " + message;
                }
                
                // 在主游戏线程中执行
                client.execute(() -> {
                    try {
                        // 发送命令（sendCommand需要去掉开头的/）
                        client.getNetworkHandler().sendCommand(command);
                        System.out.println("[服务器信息API] 已发送命令: /" + command);
                    } catch (Exception e) {
                        System.err.println("[服务器信息API] 发送命令时出错: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
                
                return true;
            } catch (Exception e) {
                System.err.println("[服务器信息API] 执行命令时出错: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }
        
        /**
         * 发送成功响应
         */
        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(statusCode, response.getBytes(StandardCharsets.UTF_8).length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
        
        /**
         * 发送错误响应
         */
        private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
            String errorResponse = String.format("{\"error\":\"%s\"}", escapeJson(message));
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(statusCode, errorResponse.getBytes(StandardCharsets.UTF_8).length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(errorResponse.getBytes(StandardCharsets.UTF_8));
            }
        }
        
        /**
         * 转义JSON字符串
         */
        private String escapeJson(String str) {
            if (str == null) {
                return "";
            }
            return str.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }
    }
}