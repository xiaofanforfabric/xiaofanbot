package com.xiaofan.world.autosave.java;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class webserver {
    private static final int HTTP_PORT = 8081;
    private static HttpServer httpServer;
    private static final ConcurrentHashMap<String, String> chatHistory = new ConcurrentHashMap<>();
    private static boolean isInGame = false;
    private static boolean isEnabled = true;

    public static void initialize() {
        // 监听所有游戏消息（包括系统消息、玩家聊天、死亡消息等）
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (isEnabled) {
                String messageText = message.getString();
                addChatMessage(messageText);
            }
            return true;
        });

        // 监听所有聊天消息（修正：需要5个参数）
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            if (isEnabled) {
                String messageText = message.getString();
                addChatMessage(messageText);
            }
        });

        // 监听游戏世界进入（包括单人游戏）
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            isInGame = true;
            if (isEnabled) {
                startHttpServer();
            }
            addSystemMessage("已进入游戏世界");
        });

        // 监听游戏世界退出
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            isInGame = false;
            stopHttpServer();
            addSystemMessage("已退出游戏世界");
        });

        // 启动时立即开启HTTP服务器（即使不在游戏中）
        if (isEnabled) {
            startHttpServer();
        }
    }

    public static void setEnabled(boolean enabled) {
        isEnabled = enabled;
        if (!enabled) {
            stopHttpServer();
        } else {
            startHttpServer();
        }
    }

    public static boolean isEnabled() {
        return isEnabled;
    }

    private static void startHttpServer() {
        if (httpServer != null) {
            return;
        }

        try {
            httpServer = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
            httpServer.createContext("/", new WebInterfaceHandler());
            httpServer.createContext("/chat", new ChatHandler());
            httpServer.createContext("/status", new StatusHandler());
            httpServer.createContext("/clear", new ClearHandler());
            httpServer.createContext("/latest", new LatestHandler());
            httpServer.createContext("/send", new SendHandler()); // 新增发送消息接口
            httpServer.setExecutor(Executors.newCachedThreadPool());
            httpServer.start();

            addSystemMessage("§a[聊天同步] HTTP服务器已启动 (端口: " + HTTP_PORT + ")");
            addSystemMessage("§a[聊天同步] 访问 http://localhost:" + HTTP_PORT + " 查看聊天监控");

        } catch (IOException e) {
            addSystemMessage("§c[聊天同步] 启动失败: " + e.getMessage());
        }
    }

    private static void stopHttpServer() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
            addSystemMessage("§e[聊天同步] HTTP服务器已停止");
        }
    }

    public static void addChatMessage(String message) {
        if (!isEnabled || message.trim().isEmpty()) return;

        String timestamp = String.valueOf(System.currentTimeMillis());
        chatHistory.put(timestamp, message);

        if (chatHistory.size() > 1000) {
            String oldestKey = chatHistory.keys().nextElement();
            chatHistory.remove(oldestKey);
        }
    }

    public static void clearChatHistory() {
        chatHistory.clear();
        addSystemMessage("§a[聊天同步] 聊天记录已清空");
    }

    // 发送消息到游戏
    public static boolean sendToGame(String message) {
        if (!isInGame || message.trim().isEmpty()) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.getNetworkHandler() != null) {
            String trimmedMessage = message.trim();

            // 如果是命令（以/开头），直接发送命令
            if (trimmedMessage.startsWith("/")) {
                client.getNetworkHandler().sendCommand(trimmedMessage.substring(1));
            } else {
                // 普通聊天消息
                client.getNetworkHandler().sendChatMessage(trimmedMessage);
            }

            // 记录发送的消息
            addChatMessage("【网页发送】" + trimmedMessage);
            return true;
        }
        return false;
    }

    private static void addSystemMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
        addChatMessage(message);
    }

    private static String getGameStatus() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getCurrentServerEntry() != null) {
            return "multiplayer - " + client.getCurrentServerEntry().address;
        } else if (client.isInSingleplayer()) {
            return "singleplayer";
        } else {
            return "menu";
        }
    }

    // 发送消息处理器
    private static class SendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            // 获取查询参数
            String query = exchange.getRequestURI().getQuery();
            if (query == null || !query.startsWith("message=")) {
                sendError(exchange, 400, "Missing message parameter");
                return;
            }

            String message = query.substring("message=".length());
            message = java.net.URLDecoder.decode(message, StandardCharsets.UTF_8);

            boolean success = sendToGame(message);
            String response;

            if (success) {
                response = "{\"status\":\"success\",\"message\":\"Message sent to game\"}";
            } else {
                response = "{\"status\":\"error\",\"message\":\"Failed to send message - not in game\"}";
            }

            sendResponse(exchange, response);
        }
    }

    // 网页界面处理器（更新版）
    private static class WebInterfaceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            String html = getWebInterfaceHTML();
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        private String getWebInterfaceHTML() {
            return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Minecraft 聊天监控</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { 
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        background: linear-gradient(135deg, #1e3c72 0%, #2a5298 100%);
                        color: #fff; min-height: 100vh; 
                    }
                    .container { max-width: 1200px; margin: 0 auto; padding: 20px; }
                    .header { 
                        text-align: center; margin-bottom: 30px; padding: 20px;
                        background: rgba(255, 255, 255, 0.1); border-radius: 15px;
                        backdrop-filter: blur(10px); 
                    }
                    .header h1 { font-size: 2.5em; margin-bottom: 10px; text-shadow: 2px 2px 4px rgba(0,0,0,0.5); }
                    .status-bar { 
                        display: flex; justify-content: space-between; align-items: center;
                        margin-bottom: 20px; padding: 15px; background: rgba(255,255,255,0.08);
                        border-radius: 10px; flex-wrap: wrap; gap: 10px;
                    }
                    .status-item { display: flex; align-items: center; gap: 10px; }
                    .status-dot { 
                        width: 12px; height: 12px; border-radius: 50%; 
                        background: #4CAF50; animation: pulse 2s infinite; 
                    }
                    .status-dot.offline { background: #f44336; }
                    @keyframes pulse { 
                        0% { opacity: 1; } 50% { opacity: 0.5; } 100% { opacity: 1; } 
                    }
                    .chat-container { 
                        background: rgba(255, 255, 255, 0.05); border-radius: 15px;
                        padding: 20px; height: 500px; overflow-y: auto; margin-bottom: 20px;
                    }
                    .message { 
                        margin-bottom: 15px; padding: 12px; border-radius: 8px;
                        background: rgba(255, 255, 255, 0.1); border-left: 4px solid #4CAF50;
                    }
                    .message.system { border-left-color: #2196F3; }
                    .message.death { border-left-color: #f44336; }
                    .message.web { border-left-color: #FF9800; }
                    .message-time { 
                        font-size: 0.8em; color: #ccc; margin-bottom: 5px; 
                    }
                    .message-content { font-size: 1.1em; }
                    .send-container {
                        background: rgba(255, 255, 255, 0.08); padding: 20px;
                        border-radius: 10px; margin-bottom: 20px;
                    }
                    .send-form {
                        display: flex; gap: 10px;
                    }
                    .message-input {
                        flex: 1; padding: 12px; border: none; border-radius: 8px;
                        background: rgba(255, 255, 255, 0.1); color: white;
                        font-size: 1em;
                    }
                    .message-input:focus {
                        outline: none; background: rgba(255, 255, 255, 0.15);
                    }
                    .send-btn {
                        padding: 12px 24px; border: none; border-radius: 8px;
                        background: linear-gradient(45deg, #4CAF50, #45a049);
                        color: white; cursor: pointer; font-size: 1em;
                        transition: transform 0.2s; 
                    }
                    .send-btn:hover { transform: translateY(-2px); }
                    .send-btn:active { transform: translateY(0); }
                    .controls { 
                        display: flex; gap: 15px; margin-top: 20px; 
                        justify-content: center; flex-wrap: wrap;
                    }
                    button { 
                        padding: 12px 24px; border: none; border-radius: 8px;
                        background: linear-gradient(45deg, #FF416C, #FF4B2B);
                        color: white; cursor: pointer; font-size: 1em;
                        transition: transform 0.2s; 
                    }
                    button:hover { transform: translateY(-2px); }
                    button:active { transform: translateY(0); }
                    .refresh-btn { background: linear-gradient(45deg, #2196F3, #1976D2); }
                    .clear-btn { background: linear-gradient(45deg, #f44336, #d32f2f); }
                    .stats { 
                        text-align: center; margin-top: 20px; font-size: 0.9em;
                        color: #ccc; 
                    }
                    .help-text {
                        font-size: 0.9em; color: #ccc; margin-top: 10px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🎮 Minecraft 聊天监控</h1>
                        <p>实时显示游戏聊天消息 | 支持网页发送消息和命令</p>
                    </div>
                    
                    <div class="status-bar">
                        <div class="status-item">
                            <div class="status-dot" id="statusDot"></div>
                            <span id="connectionStatus">连接状态: 检查中...</span>
                        </div>
                        <div class="status-item">
                            <span id="messageCount">消息数量: 0</span>
                        </div>
                        <div class="status-item">
                            <span id="gameStatus">游戏状态: 未知</span>
                        </div>
                    </div>
                    
                    <div class="send-container">
                        <div class="send-form">
                            <input type="text" id="messageInput" class="message-input" 
                                   placeholder="输入消息或命令（命令以/开头）..." maxlength="256">
                            <button class="send-btn" onclick="sendMessage()">📤 发送</button>
                        </div>
                        <div class="help-text">
                            提示：以 / 开头的消息将作为命令执行，例如 /time set day
                        </div>
                    </div>
                    
                    <div class="chat-container" id="chatContainer">
                        <div class="message system">
                            <div class="message-time">系统消息</div>
                            <div class="message-content">聊天监控已启动，等待消息...</div>
                        </div>
                    </div>
                    
                    <div class="controls">
                        <button class="refresh-btn" onclick="loadMessages()">🔄 刷新消息</button>
                        <button class="clear-btn" onclick="clearMessages()">🗑️ 清空记录</button>
                    </div>
                    
                    <div class="stats">
                        <p>端口: 8081 | 最后更新: <span id="lastUpdate">-</span></p>
                    </div>
                </div>

                <script>
                    let autoRefresh = true;
                    let lastMessageCount = 0;

                    document.addEventListener('DOMContentLoaded', function() {
                        updateStatus();
                        loadMessages();
                        setInterval(updateStatus, 5000);
                        setInterval(() => {
                            if (autoRefresh) {
                                loadMessages();
                            }
                        }, 2000);
                        
                        // 回车发送消息
                        document.getElementById('messageInput').addEventListener('keypress', function(e) {
                            if (e.key === 'Enter') {
                                sendMessage();
                            }
                        });
                    });

                    async function updateStatus() {
                        try {
                            const response = await fetch('/status');
                            const data = await response.json();
                            
                            document.getElementById('statusDot').className = 
                                data.server_running ? 'status-dot' : 'status-dot offline';
                            document.getElementById('connectionStatus').textContent = 
                                '连接状态: ' + (data.server_running ? '已连接' : '未连接');
                            document.getElementById('messageCount').textContent = 
                                '消息数量: ' + data.message_count;
                            document.getElementById('gameStatus').textContent = 
                                '游戏状态: ' + data.game_status;
                            
                        } catch (error) {
                            console.error('状态更新失败:', error);
                        }
                    }

                    async function loadMessages() {
                        try {
                            const response = await fetch('/latest?limit=50');
                            const data = await response.json();
                            
                            const container = document.getElementById('chatContainer');
                            
                            if (data.latest_messages.length !== lastMessageCount) {
                                lastMessageCount = data.latest_messages.length;
                                
                                container.innerHTML = '';
                                
                                data.latest_messages.reverse().forEach(msg => {
                                    const messageDiv = document.createElement('div');
                                    messageDiv.className = getMessageClass(msg.content);
                                    
                                    const time = new Date(parseInt(msg.timestamp));
                                    const timeStr = time.toLocaleTimeString();
                                    
                                    messageDiv.innerHTML = `
                                        <div class="message-time">${timeStr}</div>
                                        <div class="message-content">${escapeHtml(msg.content)}</div>
                                    `;
                                    
                                    container.appendChild(messageDiv);
                                });
                                
                                container.scrollTop = container.scrollHeight;
                                document.getElementById('lastUpdate').textContent = new Date().toLocaleTimeString();
                            }
                            
                        } catch (error) {
                            console.error('加载消息失败:', error);
                        }
                    }

                    async function sendMessage() {
                        const input = document.getElementById('messageInput');
                        const message = input.value.trim();
                        
                        if (!message) {
                            alert('请输入消息内容');
                            return;
                        }
                        
                        if (!(await checkGameStatus())) {
                            alert('无法发送消息：未连接到游戏');
                            return;
                        }
                        
                        try {
                            const response = await fetch('/send?message=' + encodeURIComponent(message), {
                                method: 'POST'
                            });
                            
                            const result = await response.json();
                            
                            if (result.status === 'success') {
                                input.value = '';
                                loadMessages(); // 刷新显示发送的消息
                            } else {
                                alert('发送失败: ' + result.message);
                            }
                            
                        } catch (error) {
                            console.error('发送失败:', error);
                            alert('发送失败，请检查连接');
                        }
                    }

                    async function checkGameStatus() {
                        try {
                            const response = await fetch('/status');
                            const data = await response.json();
                            return data.in_game && data.server_running;
                        } catch (error) {
                            return false;
                        }
                    }

                    async function clearMessages() {
                        if (confirm('确定要清空所有聊天记录吗？')) {
                            try {
                                await fetch('/clear', { method: 'POST' });
                                loadMessages();
                                alert('聊天记录已清空！');
                            } catch (error) {
                                console.error('清空失败:', error);
                                alert('清空失败！');
                            }
                        }
                    }

                    function getMessageClass(content) {
                        if (content.includes('死亡') || content.includes('died') || content.includes('was slain')) {
                            return 'message death';
                        } else if (content.includes('【网页发送】')) {
                            return 'message web';
                        } else if (content.includes('加入') || content.includes('离开') || 
                                 content.includes('achievement') || content.includes('进度')) {
                            return 'message system';
                        }
                        return 'message';
                    }

                    function escapeHtml(text) {
                        const div = document.createElement('div');
                        div.textContent = text;
                        return div.innerHTML;
                    }
                </script>
            </body>
            </html>
            """;
        }
    }

    // 原有的其他处理器保持不变...
    private static class ChatHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            StringBuilder json = new StringBuilder("{\"messages\":[");
            chatHistory.forEach((time, msg) -> {
                String escaped = escapeJson(msg);
                json.append(String.format("{\"timestamp\":%s,\"content\":\"%s\"},", time, escaped));
            });

            if (json.charAt(json.length()-1) == ',') {
                json.deleteCharAt(json.length()-1);
            }

            json.append("],\"count\":").append(chatHistory.size())
                    .append(",\"game_status\":\"").append(getGameStatus())
                    .append("\"}");

            sendResponse(exchange, json.toString());
        }
    }

    private static class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            String response = String.format(
                    "{\"enabled\":%s,\"in_game\":%s,\"game_status\":\"%s\",\"port\":%d,\"message_count\":%d,\"server_running\":%s}",
                    isEnabled, isInGame, getGameStatus(), HTTP_PORT, chatHistory.size(), (httpServer != null)
            );

            sendResponse(exchange, response);
        }
    }

    private static class ClearHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            clearChatHistory();
            sendResponse(exchange, "{\"status\":\"success\",\"message\":\"Chat history cleared\"}");
        }
    }

    private static class LatestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            int limit = 50;

            if (query != null && query.startsWith("limit=")) {
                try {
                    limit = Integer.parseInt(query.substring("limit=".length()));
                    limit = Math.min(Math.max(limit, 1), 100);
                } catch (NumberFormatException e) {
                    // 使用默认值
                }
            }

            StringBuilder json = new StringBuilder("{\"latest_messages\":[");

            chatHistory.entrySet().stream()
                    .sorted((e1, e2) -> Long.compare(Long.parseLong(e2.getKey()), Long.parseLong(e1.getKey())))
                    .limit(limit)
                    .forEach(entry -> {
                        String escaped = escapeJson(entry.getValue());
                        json.append(String.format("{\"timestamp\":%s,\"content\":\"%s\"},", entry.getKey(), escaped));
                    });

            if (json.charAt(json.length()-1) == ',') {
                json.deleteCharAt(json.length()-1);
            }

            json.append("],\"count\":").append(chatHistory.size()).append("}");

            sendResponse(exchange, json.toString());
        }
    }

    // 工具方法保持不变...
    private static String escapeJson(String input) {
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static void sendResponse(HttpExchange exchange, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void sendError(HttpExchange exchange, int code, String message) throws IOException {
        String response = "{\"error\":\"" + escapeJson(message) + "\",\"code\":" + code + "}";
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}