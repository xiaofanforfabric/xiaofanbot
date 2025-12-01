package com.xiaofan.autosaveforforge;

import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * 宏管理 Web 服务器
 * 端口: 8079
 * 提供网页界面来管理宏文件
 */
public class MacroWebServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int HTTP_PORT = 8079;
    private static HttpServer httpServer;
    private static boolean isRunning = false;
    
    /**
     * 初始化 Web 服务器
     */
    public static void initialize() {
        if (isRunning) {
            return;
        }
        
        try {
            // 先尝试停止可能存在的旧服务器
            stop();
            
            httpServer = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
            httpServer.createContext("/", new WebInterfaceHandler());
            httpServer.createContext("/api/list", new ListMacrosHandler());
            httpServer.createContext("/api/execute", new ExecuteMacroHandler());
            httpServer.createContext("/api/stop", new StopMacroHandler());
            httpServer.createContext("/api/status", new StatusHandler());
            httpServer.setExecutor(Executors.newCachedThreadPool());
            httpServer.start();
            isRunning = true;
            
            LOGGER.info("[宏管理] Web 服务器已启动，访问 http://localhost:{} 管理宏", HTTP_PORT);
            
        } catch (java.net.BindException e) {
            LOGGER.error("[宏管理] 端口 {} 已被占用", HTTP_PORT);
            isRunning = false;
        } catch (IOException e) {
            LOGGER.error("[宏管理] 启动失败: {}", e.getMessage(), e);
            isRunning = false;
        }
    }
    
    /**
     * 停止 Web 服务器
     */
    public static void stop() {
        if (httpServer != null && isRunning) {
            try {
                httpServer.stop(0);
                LOGGER.info("[宏管理] Web 服务器已停止");
            } catch (Exception e) {
                LOGGER.error("[宏管理] 停止失败: {}", e.getMessage());
            }
            httpServer = null;
            isRunning = false;
        }
    }
    
    /**
     * Web 界面处理器
     */
    private static class WebInterfaceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }
            
            String html = generateHTML();
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
        
        private String generateHTML() {
            return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>宏管理 - Baritone 任务管理器</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: 'Microsoft YaHei', 'Segoe UI', sans-serif;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: #333;
                        padding: 20px;
                        min-height: 100vh;
                    }
                    .container {
                        max-width: 1200px;
                        margin: 0 auto;
                        background: white;
                        border-radius: 15px;
                        box-shadow: 0 10px 40px rgba(0,0,0,0.2);
                        padding: 30px;
                    }
                    h1 {
                        color: #667eea;
                        margin-bottom: 10px;
                        font-size: 2.5em;
                        text-align: center;
                    }
                    .subtitle {
                        text-align: center;
                        color: #666;
                        margin-bottom: 30px;
                    }
                    .section {
                        margin-bottom: 30px;
                        padding: 20px;
                        background: #f8f9fa;
                        border-radius: 10px;
                        border-left: 4px solid #667eea;
                    }
                    .section-title {
                        font-size: 1.3em;
                        color: #667eea;
                        margin-bottom: 15px;
                        font-weight: bold;
                    }
                    .macro-list {
                        display: grid;
                        grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
                        gap: 15px;
                        margin-top: 15px;
                    }
                    .macro-item {
                        background: white;
                        padding: 15px;
                        border-radius: 8px;
                        border: 2px solid #e0e0e0;
                        transition: all 0.3s;
                    }
                    .macro-item:hover {
                        border-color: #667eea;
                        box-shadow: 0 4px 12px rgba(102, 126, 234, 0.2);
                        transform: translateY(-2px);
                    }
                    .macro-name {
                        font-weight: bold;
                        color: #333;
                        margin-bottom: 10px;
                        font-size: 1.1em;
                    }
                    .macro-status {
                        display: inline-block;
                        padding: 4px 10px;
                        border-radius: 12px;
                        font-size: 0.85em;
                        margin-bottom: 10px;
                    }
                    .status-running {
                        background: #4caf50;
                        color: white;
                    }
                    .status-stopped {
                        background: #9e9e9e;
                        color: white;
                    }
                    .button-group {
                        display: flex;
                        gap: 8px;
                        margin-top: 10px;
                    }
                    button {
                        flex: 1;
                        padding: 8px 16px;
                        border: none;
                        border-radius: 6px;
                        cursor: pointer;
                        font-size: 0.9em;
                        font-weight: 500;
                        transition: all 0.3s;
                    }
                    .btn-execute {
                        background: #4caf50;
                        color: white;
                    }
                    .btn-execute:hover {
                        background: #45a049;
                    }
                    .btn-stop {
                        background: #f44336;
                        color: white;
                    }
                    .btn-stop:hover {
                        background: #da190b;
                    }
                    .btn-refresh {
                        background: #2196f3;
                        color: white;
                        padding: 10px 20px;
                        margin-bottom: 20px;
                    }
                    .btn-refresh:hover {
                        background: #0b7dda;
                    }
                    button:disabled {
                        background: #ccc;
                        cursor: not-allowed;
                    }
                    .status-info {
                        padding: 15px;
                        background: #e3f2fd;
                        border-radius: 8px;
                        margin-top: 15px;
                        border-left: 4px solid #2196f3;
                    }
                    .error {
                        background: #ffebee;
                        border-left-color: #f44336;
                        color: #c62828;
                    }
                    .success {
                        background: #e8f5e9;
                        border-left-color: #4caf50;
                        color: #2e7d32;
                    }
                    .loading {
                        text-align: center;
                        padding: 20px;
                        color: #666;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>🎮 Baritone 宏管理</h1>
                    <p class="subtitle">管理你的自动化任务宏文件</p>
                    
                    <div class="section">
                        <div style="display: flex; justify-content: space-between; align-items: center;">
                            <div class="section-title">📋 宏文件列表</div>
                            <button class="btn-refresh" onclick="loadMacros()">🔄 刷新</button>
                        </div>
                        <div id="statusInfo"></div>
                        <div id="macroList" class="macro-list">
                            <div class="loading">正在加载宏文件...</div>
                        </div>
                    </div>
                </div>
                
                <script>
                    let macroStatuses = {};
                    
                    async function loadMacros() {
                        const statusInfo = document.getElementById('statusInfo');
                        const macroList = document.getElementById('macroList');
                        
                        statusInfo.innerHTML = '';
                        macroList.innerHTML = '<div class="loading">正在加载宏文件...</div>';
                        
                        try {
                            // 加载宏列表
                            const listResponse = await fetch('/api/list');
                            const listData = await listResponse.json();
                            
                            if (!listData.success) {
                                macroList.innerHTML = '<div class="error">加载失败: ' + listData.message + '</div>';
                                return;
                            }
                            
                            // 加载状态
                            const statusResponse = await fetch('/api/status');
                            const statusData = await statusResponse.json();
                            if (statusData.success) {
                                macroStatuses = statusData.running || {};
                            }
                            
                            if (listData.macros.length === 0) {
                                macroList.innerHTML = '<div class="status-info">没有找到宏文件。请在 config/do/ 文件夹中创建 .txt 格式的宏文件。</div>';
                                return;
                            }
                            
                            macroList.innerHTML = listData.macros.map(macro => {
                                const isRunning = macroStatuses[macro] || false;
                                return `
                                    <div class="macro-item">
                                        <div class="macro-name">${escapeHtml(macro)}</div>
                                        <div class="macro-status ${isRunning ? 'status-running' : 'status-stopped'}">
                                            ${isRunning ? '▶ 运行中' : '⏸ 已停止'}
                                        </div>
                                        <div class="button-group">
                                            <button class="btn-execute" onclick="executeMacro('${escapeHtml(macro)}')" ${isRunning ? 'disabled' : ''}>
                                                执行
                                            </button>
                                            <button class="btn-stop" onclick="stopMacro('${escapeHtml(macro)}')" ${!isRunning ? 'disabled' : ''}>
                                                停止
                                            </button>
                                        </div>
                                    </div>
                                `;
                            }).join('');
                            
                        } catch (error) {
                            macroList.innerHTML = '<div class="error">加载失败: ' + error.message + '</div>';
                        }
                    }
                    
                    async function executeMacro(macroName) {
                        const statusInfo = document.getElementById('statusInfo');
                        statusInfo.innerHTML = '<div class="loading">正在启动宏: ' + escapeHtml(macroName) + '...</div>';
                        
                        try {
                            const response = await fetch('/api/execute?macro=' + encodeURIComponent(macroName), {
                                method: 'POST'
                            });
                            const data = await response.json();
                            
                            if (data.success) {
                                statusInfo.innerHTML = '<div class="success">✓ 宏已启动: ' + escapeHtml(macroName) + '</div>';
                                macroStatuses[macroName] = true;
                                loadMacros();
                            } else {
                                statusInfo.innerHTML = '<div class="error">✗ 启动失败: ' + escapeHtml(data.message) + '</div>';
                            }
                        } catch (error) {
                            statusInfo.innerHTML = '<div class="error">✗ 请求失败: ' + escapeHtml(error.message) + '</div>';
                        }
                    }
                    
                    async function stopMacro(macroName) {
                        const statusInfo = document.getElementById('statusInfo');
                        statusInfo.innerHTML = '<div class="loading">正在停止宏: ' + escapeHtml(macroName) + '...</div>';
                        
                        try {
                            const response = await fetch('/api/stop?macro=' + encodeURIComponent(macroName), {
                                method: 'POST'
                            });
                            const data = await response.json();
                            
                            if (data.success) {
                                statusInfo.innerHTML = '<div class="success">✓ 宏已停止: ' + escapeHtml(macroName) + '</div>';
                                macroStatuses[macroName] = false;
                                loadMacros();
                            } else {
                                statusInfo.innerHTML = '<div class="error">✗ 停止失败: ' + escapeHtml(data.message) + '</div>';
                            }
                        } catch (error) {
                            statusInfo.innerHTML = '<div class="error">✗ 请求失败: ' + escapeHtml(error.message) + '</div>';
                        }
                    }
                    
                    function escapeHtml(text) {
                        const div = document.createElement('div');
                        div.textContent = text;
                        return div.innerHTML;
                    }
                    
                    // 页面加载时自动加载宏列表
                    loadMacros();
                    
                    // 每5秒自动刷新状态
                    setInterval(() => {
                        loadMacros();
                    }, 5000);
                </script>
            </body>
            </html>
            """;
        }
        
        private void sendError(HttpExchange exchange, int code, String message) throws IOException {
            String response = "{\"error\":\"" + escapeJson(message) + "\"}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(code, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
        
        private String escapeJson(String str) {
            if (str == null) return "";
            return str.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }
    }
    
    /**
     * 列出宏文件 API
     */
    private static class ListMacrosHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }
            
            try {
                BaritoneTaskManager manager = BaritoneTaskManager.getInstance();
                if (manager.getMacroFolder() == null) {
                    manager.initialize();
                }
                
                File macroFolder = manager.getMacroFolder();
                if (macroFolder == null || !macroFolder.exists()) {
                    sendResponse(exchange, 200, "{\"success\":false,\"message\":\"宏文件夹不存在\"}");
                    return;
                }
                
                File[] files = macroFolder.listFiles((dir, name) -> name.endsWith(".txt"));
                List<String> macroNames = new ArrayList<>();
                if (files != null) {
                    for (File file : files) {
                        macroNames.add(file.getName().replace(".txt", ""));
                    }
                }
                
                String response = String.format("{\"success\":true,\"macros\":%s}", 
                    macroNames.stream()
                        .map(name -> "\"" + escapeJson(name) + "\"")
                        .reduce((a, b) -> a + "," + b)
                        .map(list -> "[" + list + "]")
                        .orElse("[]"));
                
                sendResponse(exchange, 200, response);
                
            } catch (Exception e) {
                LOGGER.error("[宏管理] 列出宏文件时出错", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }
    
    /**
     * 执行宏 API
     */
    private static class ExecuteMacroHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }
            
            String macroName = null;
            try {
                String query = exchange.getRequestURI().getQuery();
                if (query != null) {
                    for (String param : query.split("&")) {
                        String[] parts = param.split("=", 2);
                        if (parts.length == 2 && parts[0].equals("macro")) {
                            macroName = java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                            break;
                        }
                    }
                }
                
                if (macroName == null || macroName.isEmpty()) {
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"缺少 macro 参数\"}");
                    return;
                }
                
                BaritoneTaskManager manager = BaritoneTaskManager.getInstance();
                if (manager.getMacroFolder() == null) {
                    manager.initialize();
                }
                
                File macroFile = new File(manager.getMacroFolder(), macroName + ".txt");
                if (!macroFile.exists()) {
                    sendResponse(exchange, 404, "{\"success\":false,\"message\":\"宏文件不存在: " + escapeJson(macroName) + ".txt\"}");
                    return;
                }
                
                // 如果宏已经在运行，先停止
                if (manager.isMacroRunning(macroName)) {
                    manager.stopMacro(macroName);
                }
                
                // 加载并启动宏
                Macro macro = MacroParser.parse(macroFile);
                manager.loadMacro(macroName, macro);
                manager.startMacro(macroName);
                
                sendResponse(exchange, 200, "{\"success\":true,\"message\":\"宏已启动: " + escapeJson(macroName) + "\"}");
                LOGGER.info("[宏管理] 通过 Web 界面启动宏: {}", macroName);
                
            } catch (NotFanMacroFound e) {
                String errorMacroName = macroName != null ? macroName : e.getMessage();
                LOGGER.error("[宏管理] 宏不存在: {}", errorMacroName);
                sendResponse(exchange, 404, "{\"success\":false,\"message\":\"NotFanMacroFound: " + escapeJson(errorMacroName) + "\"}");
            } catch (Exception e) {
                LOGGER.error("[宏管理] 执行宏时出错", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }
    
    /**
     * 停止宏 API
     */
    private static class StopMacroHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }
            
            try {
                String query = exchange.getRequestURI().getQuery();
                String macroName = null;
                if (query != null) {
                    for (String param : query.split("&")) {
                        String[] parts = param.split("=", 2);
                        if (parts.length == 2 && parts[0].equals("macro")) {
                            macroName = java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                            break;
                        }
                    }
                }
                
                if (macroName == null || macroName.isEmpty()) {
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"缺少 macro 参数\"}");
                    return;
                }
                
                BaritoneTaskManager manager = BaritoneTaskManager.getInstance();
                if (manager.isMacroRunning(macroName)) {
                    manager.stopMacro(macroName);
                    sendResponse(exchange, 200, "{\"success\":true,\"message\":\"宏已停止: " + escapeJson(macroName) + "\"}");
                    LOGGER.info("[宏管理] 通过 Web 界面停止宏: {}", macroName);
                } else {
                    sendResponse(exchange, 200, "{\"success\":false,\"message\":\"宏未在运行: " + escapeJson(macroName) + "\"}");
                }
                
            } catch (Exception e) {
                LOGGER.error("[宏管理] 停止宏时出错", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }
    
    /**
     * 状态查询 API
     */
    private static class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }
            
            try {
                BaritoneTaskManager manager = BaritoneTaskManager.getInstance();
                java.util.Set<String> macroNames = manager.getMacroNames();
                java.util.Map<String, Boolean> running = new java.util.HashMap<>();
                
                for (String macroName : macroNames) {
                    running.put(macroName, manager.isMacroRunning(macroName));
                }
                
                String runningJson = running.entrySet().stream()
                    .map(e -> "\"" + escapeJson(e.getKey()) + "\":" + e.getValue())
                    .reduce((a, b) -> a + "," + b)
                    .map(s -> "{" + s + "}")
                    .orElse("{}");
                
                String response = "{\"success\":true,\"running\":" + runningJson + "}";
                sendResponse(exchange, 200, response);
                
            } catch (Exception e) {
                LOGGER.error("[宏管理] 查询状态时出错", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }
    
    private static void sendResponse(HttpExchange exchange, int code, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
    
    private static void sendError(HttpExchange exchange, int code, String message) throws IOException {
        sendResponse(exchange, code, "{\"error\":\"" + escapeJson(message) + "\"}");
    }
    
    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

