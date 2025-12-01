package com.xiaofan.autosaveforforge;

import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * HTTP 服务器，用于显示当前游戏状态
 * 端口: 8083
 */
public class StatusHttpServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int HTTP_PORT = 8083;
    private static HttpServer httpServer;
    private static boolean isRunning = false;
    
    // 持久连接相关
    private static boolean persistentConnection = false;
    private static String lastServerAddress = null;
    private static String lastProxyAddress = null; // 保存上次使用的代理地址
    private static long lastDisconnectTime = 0;
    private static final long RECONNECT_DELAY = 60000; // 1分钟（超大型模组服需要更长时间加载）

    /**
     * 游戏状态枚举
     */
    public enum GameStatus {
        LOADING("加载中"),
        TITLE_SCREEN("标题屏幕"),
        SINGLE_PLAYER("单人游戏"),
        MULTIPLAYER("服务器模式");

        private final String displayName;

        GameStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 获取当前游戏状态
     */
    public static GameStatusInfo getCurrentStatus() {
        Minecraft mc = Minecraft.getInstance();
        
        if (mc == null) {
            return new GameStatusInfo(GameStatus.LOADING, null, null);
        }

        // 检查是否在标题屏幕
        if (mc.screen instanceof TitleScreen) {
            return new GameStatusInfo(GameStatus.TITLE_SCREEN, null, null);
        }

        // 检查是否在游戏中
        if (mc.level != null) {
            // 检查是否是多人游戏
            if (mc.getConnection() != null && mc.getConnection().getConnection() != null) {
                ServerData serverData = mc.getCurrentServer();
                String serverAddress = serverData != null ? serverData.ip : "未知服务器";
                return new GameStatusInfo(GameStatus.MULTIPLAYER, serverAddress, null);
            } else {
                // 单人游戏
                String worldName = mc.getSingleplayerServer() != null ? 
                    mc.getSingleplayerServer().getWorldData().getLevelName() : "未知世界";
                return new GameStatusInfo(GameStatus.SINGLE_PLAYER, null, worldName);
            }
        }

        // 默认状态：加载中
        return new GameStatusInfo(GameStatus.LOADING, null, null);
    }

    /**
     * 游戏状态信息
     */
    public static class GameStatusInfo {
        public final GameStatus status;
        public final String serverAddress;
        public final String worldName;

        public GameStatusInfo(GameStatus status, String serverAddress, String worldName) {
            this.status = status;
            this.serverAddress = serverAddress;
            this.worldName = worldName;
        }
    }

    /**
     * 初始化 HTTP 服务器
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
            httpServer.createContext("/status", new StatusHandler());
            httpServer.createContext("/connect", new ConnectHandler());
            httpServer.createContext("/disconnect", new DisconnectHandler());
            httpServer.createContext("/togglePersistent", new TogglePersistentHandler());
            httpServer.setExecutor(Executors.newCachedThreadPool());
            httpServer.start();
            isRunning = true;
            
            // 注册自动重连监听器
            MinecraftForge.EVENT_BUS.register(new AutoReconnectListener());

            LOGGER.info("[状态服务器] HTTP服务器已启动 (端口: {})", HTTP_PORT);
            LOGGER.info("[状态服务器] 访问 http://localhost:{} 查看游戏状态", HTTP_PORT);

        } catch (java.net.BindException e) {
            LOGGER.error("[状态服务器] 端口 {} 已被占用", HTTP_PORT);
            isRunning = false;
        } catch (IOException e) {
            LOGGER.error("[状态服务器] 启动失败: {}", e.getMessage(), e);
            isRunning = false;
        }
    }

    /**
     * 停止 HTTP 服务器
     */
    public static void stop() {
        if (httpServer != null && isRunning) {
            try {
                httpServer.stop(0);
                LOGGER.info("[状态服务器] HTTP服务器已停止");
            } catch (Exception e) {
                LOGGER.error("[状态服务器] 停止失败: {}", e.getMessage());
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
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }

            String html = generateHTML();
            sendResponse(exchange, html, "text/html; charset=utf-8");
        }
    }

    /**
     * 状态 API 处理器
     */
    private static class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }

            GameStatusInfo status = getCurrentStatus();
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"status\":\"").append(status.status.name()).append("\",");
            json.append("\"displayName\":\"").append(escapeJson(status.status.getDisplayName())).append("\",");
            json.append("\"persistentConnection\":").append(persistentConnection);
            
            if (status.serverAddress != null) {
                json.append(",\"serverAddress\":\"").append(escapeJson(status.serverAddress)).append("\"");
            }
            
            if (status.worldName != null) {
                json.append(",\"worldName\":\"").append(escapeJson(status.worldName)).append("\"");
            }
            
            json.append("}");
            
            sendResponse(exchange, json.toString(), "application/json; charset=utf-8");
        }
    }

    /**
     * 连接服务器处理器
     */
    private static class ConnectHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }

            try {
                // 读取请求体
                InputStream requestBody = exchange.getRequestBody();
                String body = new String(requestBody.readAllBytes(), StandardCharsets.UTF_8);
                
                // 解析服务器地址和代理地址
                String serverAddress = null;
                String proxyAddress = null;
                
                if (body.contains("address=")) {
                    String addressPart = body.substring(body.indexOf("address=") + 8);
                    if (addressPart.contains("&")) {
                        serverAddress = addressPart.substring(0, addressPart.indexOf("&"));
                    } else {
                        serverAddress = addressPart;
                    }
                    serverAddress = java.net.URLDecoder.decode(serverAddress, StandardCharsets.UTF_8);
                }
                
                if (body.contains("proxy=")) {
                    String proxyPart = body.substring(body.indexOf("proxy=") + 6);
                    if (proxyPart.contains("&")) {
                        proxyAddress = proxyPart.substring(0, proxyPart.indexOf("&"));
                    } else {
                        proxyAddress = proxyPart;
                    }
                    proxyAddress = java.net.URLDecoder.decode(proxyAddress, StandardCharsets.UTF_8);
                    if (proxyAddress.trim().isEmpty()) {
                        proxyAddress = null;
                    }
                }
                
                if (serverAddress == null || serverAddress.trim().isEmpty()) {
                    sendError(exchange, 400, "服务器地址不能为空");
                    return;
                }

                boolean success = connectToServer(serverAddress, proxyAddress);
                if (success) {
                    sendResponse(exchange, "{\"success\":true,\"message\":\"正在连接服务器: " + escapeJson(serverAddress) + "\"}", "application/json; charset=utf-8");
                } else {
                    sendError(exchange, 500, "连接失败");
                }
            } catch (Exception e) {
                LOGGER.error("处理连接请求失败: {}", e.getMessage(), e);
                sendError(exchange, 500, "服务器错误: " + e.getMessage());
            }
        }
    }

    /**
     * 断开连接处理器
     */
    private static class DisconnectHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }

            // 停止自动重连
            persistentConnection = false;
            lastServerAddress = null;
            lastDisconnectTime = 0;

            boolean success = disconnectFromServer();
            if (success) {
                sendResponse(exchange, "{\"success\":true,\"message\":\"已断开连接\"}", "application/json; charset=utf-8");
            } else {
                sendError(exchange, 500, "断开连接失败");
            }
        }
    }

    /**
     * 切换持久连接处理器
     */
    private static class TogglePersistentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }

            persistentConnection = !persistentConnection;
            String message = persistentConnection ? "持久连接已启用" : "持久连接已禁用";
            
            sendResponse(exchange, "{\"success\":true,\"persistentConnection\":" + persistentConnection + ",\"message\":\"" + message + "\"}", "application/json; charset=utf-8");
        }
    }

    /**
     * 连接服务器
     * @param address 服务器地址
     * @param proxyAddress 代理服务器地址（可选，如果为null则直接连接）
     */
    private static boolean connectToServer(String address, String proxyAddress) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return false;
        }

        try {
            lastServerAddress = address;
            lastProxyAddress = proxyAddress;
            
            // 如果提供了代理地址，设置系统属性以使用 SOCKS5 代理
            if (proxyAddress != null && !proxyAddress.trim().isEmpty()) {
                // 解析代理地址
                String proxyHost;
                int proxyPort;
                String[] proxyParts = proxyAddress.split(":");
                if (proxyParts.length == 2) {
                    proxyHost = proxyParts[0];
                    proxyPort = Integer.parseInt(proxyParts[1]);
                } else if (proxyParts.length == 1) {
                    proxyHost = proxyAddress;
                    proxyPort = 7000; // 默认端口
                } else {
                    LOGGER.error("[连接] 代理地址格式错误: {}", proxyAddress);
                    return false;
                }
                
                // 设置系统属性以使用 SOCKS5 代理
                System.setProperty("socksProxyHost", proxyHost);
                System.setProperty("socksProxyPort", String.valueOf(proxyPort));
                LOGGER.info("[连接] 已设置 SOCKS5 代理: {}:{}", proxyHost, proxyPort);
            } else {
                // 清除代理设置
                System.clearProperty("socksProxyHost");
                System.clearProperty("socksProxyPort");
                LOGGER.info("[连接] 已清除代理设置，直接连接");
            }
            
            final String finalAddress = address;
            mc.execute(() -> {
                try {
                    // 解析目标服务器地址
                    String[] addressParts = finalAddress.split(":");
                    String targetHost = addressParts[0];
                    int targetPort = addressParts.length > 1 ? Integer.parseInt(addressParts[1]) : 25565;
                    
                    ServerAddress serverAddress = ServerAddress.parseString(finalAddress);
                    // ServerData 中保存实际的目标服务器地址
                    ServerData serverData = new ServerData("自定义服务器", finalAddress, false);
                    
                    ConnectScreen.startConnecting(
                        mc.screen instanceof TitleScreen ? (TitleScreen) mc.screen : new TitleScreen(),
                        mc,
                        serverAddress,
                        serverData,
                        false
                    );
                    
                    if (proxyAddress != null && !proxyAddress.trim().isEmpty()) {
                        LOGGER.info("[连接] 正在通过 SOCKS5 代理 {} 连接到服务器: {}", proxyAddress, finalAddress);
                    } else {
                        LOGGER.info("[连接] 正在连接到服务器: {}", finalAddress);
                    }
                } catch (Exception e) {
                    LOGGER.error("连接服务器失败: {}", e.getMessage(), e);
                }
            });
            return true;
        } catch (Exception e) {
            LOGGER.error("连接服务器异常: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 断开服务器连接
     */
    private static boolean disconnectFromServer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return false;
        }

        try {
            mc.execute(() -> {
                try {
                    // 断开当前连接
                    if (mc.getConnection() != null && mc.getConnection().getConnection() != null) {
                        mc.getConnection().getConnection().disconnect(net.minecraft.network.chat.Component.literal("手动断开连接"));
                        LOGGER.info("[断开连接] 已断开服务器连接");
                    }
                    
                    // 返回标题页面
                    mc.setScreen(new TitleScreen());
                    LOGGER.info("[断开连接] 已返回标题页面");
                } catch (Exception e) {
                    LOGGER.error("断开连接失败: {}", e.getMessage(), e);
                }
            });
            return true;
        } catch (Exception e) {
            LOGGER.error("断开连接异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 自动重连监听器
     */
    @Mod.EventBusSubscriber(modid = Autosaveforforge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class AutoReconnectListener {
        private static boolean wasInGame = false;
        
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                return;
            }

            // 检测是否从游戏中断开
            boolean isInGame = mc.level != null && mc.getConnection() != null;
            if (wasInGame && !isInGame && persistentConnection && lastServerAddress != null) {
                // 刚刚断开连接
                lastDisconnectTime = System.currentTimeMillis();
                LOGGER.info("[自动重连] 检测到断开连接，将在1分钟后重连（超大型模组服需要更长时间加载）");
            }
            wasInGame = isInGame;

            // 如果启用了持久连接且在标题屏幕
            if (persistentConnection && lastServerAddress != null) {
                if (mc.screen instanceof TitleScreen && !isInGame) {
                    // 检查是否应该重连
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastDisconnectTime >= RECONNECT_DELAY && lastDisconnectTime > 0) {
                        LOGGER.info("[自动重连] 正在重新连接到服务器: {}", lastServerAddress);
                        connectToServer(lastServerAddress, lastProxyAddress); // 使用上次的代理设置
                        lastDisconnectTime = 0; // 重置，避免重复连接
                    }
                }
            }
        }
    }

    /**
     * 生成 HTML 页面
     */
    private static String generateHTML() {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <title>Forge 游戏状态监控</title>\n" +
                "    <style>\n" +
                "        body { font-family: 'Consolas', 'Monaco', monospace; margin: 0; padding: 20px; background: #1e1e1e; color: #d4d4d4; }\n" +
                "        h1 { color: #4ec9b0; margin-bottom: 10px; }\n" +
                "        .status-card, .control-card { background: #252526; border: 1px solid #3e3e42; border-radius: 8px; padding: 30px; margin: 20px 0; }\n" +
                "        .status-title, .control-title { font-size: 1.5em; color: #4ec9b0; margin-bottom: 20px; }\n" +
                "        .status-value { font-size: 2em; font-weight: bold; margin: 20px 0; }\n" +
                "        .status-value.LOADING { color: #808080; }\n" +
                "        .status-value.TITLE_SCREEN { color: #4ec9b0; }\n" +
                "        .status-value.SINGLE_PLAYER { color: #569cd6; }\n" +
                "        .status-value.MULTIPLAYER { color: #dcdcaa; }\n" +
                "        .status-detail { color: #808080; font-size: 1.2em; margin-top: 10px; }\n" +
                "        .status-info { color: #d4d4d4; margin-top: 15px; padding-top: 15px; border-top: 1px solid #3e3e42; }\n" +
                "        .refresh-info { color: #808080; font-size: 0.9em; margin-top: 20px; }\n" +
                "        .control-group { margin: 15px 0; }\n" +
                "        .control-group label { display: block; margin-bottom: 5px; color: #d4d4d4; }\n" +
                "        .control-group input[type=\"text\"] { width: 100%; padding: 10px; background: #1e1e1e; border: 1px solid #3e3e42; border-radius: 4px; color: #d4d4d4; font-size: 1em; box-sizing: border-box; }\n" +
                "        .control-group input[type=\"text\"]:focus { outline: none; border-color: #4ec9b0; }\n" +
                "        .button-group { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 15px; }\n" +
                "        button { padding: 10px 20px; background: #0e639c; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 1em; font-family: inherit; }\n" +
                "        button:hover { background: #1177bb; }\n" +
                "        button:active { background: #0a4d73; }\n" +
                "        button.preset { background: #4ec9b0; }\n" +
                "        button.preset:hover { background: #5dd9c0; }\n" +
                "        button.danger { background: #f48771; }\n" +
                "        button.danger:hover { background: #ff9a85; }\n" +
                "        .switch-group { display: flex; align-items: center; gap: 10px; margin-top: 15px; }\n" +
                "        .switch { position: relative; display: inline-block; width: 50px; height: 24px; }\n" +
                "        .switch input { opacity: 0; width: 0; height: 0; }\n" +
                "        .slider { position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0; background-color: #3e3e42; transition: .4s; border-radius: 24px; }\n" +
                "        .slider:before { position: absolute; content: \"\"; height: 18px; width: 18px; left: 3px; bottom: 3px; background-color: white; transition: .4s; border-radius: 50%; }\n" +
                "        input:checked + .slider { background-color: #4ec9b0; }\n" +
                "        input:checked + .slider:before { transform: translateX(26px); }\n" +
                "        .message { margin-top: 10px; padding: 10px; border-radius: 4px; display: none; }\n" +
                "        .message.success { background: #4ec9b0; color: #1e1e1e; }\n" +
                "        .message.error { background: #f48771; color: white; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <h1>Forge 游戏状态监控</h1>\n" +
                "    <div class=\"status-card\">\n" +
                "        <div class=\"status-title\">当前状态</div>\n" +
                "        <div class=\"status-value\" id=\"statusValue\">加载中...</div>\n" +
                "        <div class=\"status-detail\" id=\"statusDetail\"></div>\n" +
                "        <div class=\"status-info\" id=\"statusInfo\"></div>\n" +
                "        <div class=\"refresh-info\">自动刷新: 每 500ms</div>\n" +
                "    </div>\n" +
                "    <div class=\"control-card\">\n" +
                "        <div class=\"control-title\">服务器连接</div>\n" +
                "        <div class=\"control-group\">\n" +
                "            <label for=\"serverAddress\">服务器地址:</label>\n" +
                "            <input type=\"text\" id=\"serverAddress\" placeholder=\"例如: play.xiaoli.top 或 mc.example.com:25565\" />\n" +
                "        </div>\n" +
                "        <div class=\"control-group\">\n" +
                "            <label for=\"proxyAddress\">SOCKS5 代理服务器地址 (可选):</label>\n" +
                "            <input type=\"text\" id=\"proxyAddress\" placeholder=\"例如: 123.45.67.89:7000 或留空直接连接\" />\n" +
                "        </div>\n" +
                "        <div class=\"button-group\">\n" +
                "            <button onclick=\"connectToServer()\">连接服务器</button>\n" +
                "            <button class=\"preset\" onclick=\"connectPreset()\">连接 play.xiaoli.top</button>\n" +
                "            <button class=\"danger\" onclick=\"disconnectFromServer()\">强制断开</button>\n" +
                "        </div>\n" +
                "        <div class=\"switch-group\">\n" +
                "            <label class=\"switch\">\n" +
                "                <input type=\"checkbox\" id=\"persistentSwitch\" onchange=\"togglePersistent()\" />\n" +
                "                <span class=\"slider\"></span>\n" +
                "            </label>\n" +
                "            <label for=\"persistentSwitch\">持久连接（断开后1分钟自动重连，适合超大型模组服）</label>\n" +
                "        </div>\n" +
                "        <div class=\"message\" id=\"message\"></div>\n" +
                "    </div>\n" +
                "    <script>\n" +
                "        let persistentConnection = false;\n" +
                "\n" +
                "        function updateStatus() {\n" +
                "            fetch('/status')\n" +
                "                .then(response => response.json())\n" +
                "                .then(data => {\n" +
                "                    const statusValue = document.getElementById('statusValue');\n" +
                "                    const statusDetail = document.getElementById('statusDetail');\n" +
                "                    const statusInfo = document.getElementById('statusInfo');\n" +
                "                    \n" +
                "                    statusValue.textContent = data.displayName;\n" +
                "                    statusValue.className = 'status-value ' + data.status;\n" +
                "                    \n" +
                "                    statusDetail.innerHTML = '';\n" +
                "                    statusInfo.innerHTML = '';\n" +
                "                    \n" +
                "                    if (data.serverAddress) {\n" +
                "                        statusDetail.innerHTML = '服务器地址: <strong>' + escapeHtml(data.serverAddress) + '</strong>';\n" +
                "                    } else if (data.worldName) {\n" +
                "                        statusDetail.innerHTML = '世界名称: <strong>' + escapeHtml(data.worldName) + '</strong>';\n" +
                "                    }\n" +
                "                    \n" +
                "                    const timestamp = new Date().toLocaleString('zh-CN');\n" +
                "                    statusInfo.innerHTML = '最后更新: ' + timestamp;\n" +
                "                    \n" +
                "                    // 更新持久连接开关状态\n" +
                "                    if (data.persistentConnection !== undefined) {\n" +
                "                        persistentConnection = data.persistentConnection;\n" +
                "                        document.getElementById('persistentSwitch').checked = persistentConnection;\n" +
                "                    }\n" +
                "                })\n" +
                "                .catch(error => {\n" +
                "                    document.getElementById('statusValue').textContent = '连接错误';\n" +
                "                    document.getElementById('statusDetail').textContent = error.message;\n" +
                "                });\n" +
                "        }\n" +
                "\n" +
                "        function escapeHtml(text) {\n" +
                "            const div = document.createElement('div');\n" +
                "            div.textContent = text;\n" +
                "            return div.innerHTML;\n" +
                "        }\n" +
                "\n" +
                "        function showMessage(text, isError) {\n" +
                "            const messageDiv = document.getElementById('message');\n" +
                "            messageDiv.textContent = text;\n" +
                "            messageDiv.className = 'message ' + (isError ? 'error' : 'success');\n" +
                "            messageDiv.style.display = 'block';\n" +
                "            setTimeout(() => { messageDiv.style.display = 'none'; }, 3000);\n" +
                "        }\n" +
                "\n" +
                "        function connectToServer() {\n" +
                "            const address = document.getElementById('serverAddress').value.trim();\n" +
                "            if (!address) {\n" +
                "                showMessage('请输入服务器地址', true);\n" +
                "                return;\n" +
                "            }\n" +
                "\n" +
                "            const proxyAddress = document.getElementById('proxyAddress').value.trim();\n" +
                "            let formData = 'address=' + encodeURIComponent(address);\n" +
                "            if (proxyAddress) {\n" +
                "                formData += '&proxy=' + encodeURIComponent(proxyAddress);\n" +
                "            }\n" +
                "            fetch('/connect', {\n" +
                "                method: 'POST',\n" +
                "                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },\n" +
                "                body: formData\n" +
                "            })\n" +
                "            .then(response => response.json())\n" +
                "            .then(data => {\n" +
                "                if (data.success) {\n" +
                "                    showMessage(data.message || '正在连接服务器...', false);\n" +
                "                } else {\n" +
                "                    showMessage(data.message || '连接失败', true);\n" +
                "                }\n" +
                "            })\n" +
                "            .catch(error => {\n" +
                "                showMessage('连接失败: ' + error.message, true);\n" +
                "            });\n" +
                "        }\n" +
                "\n" +
                "        function connectPreset() {\n" +
                "            document.getElementById('serverAddress').value = 'play.xiaoli.top';\n" +
                "            connectToServer();\n" +
                "        }\n" +
                "\n" +
                "        function disconnectFromServer() {\n" +
                "            fetch('/disconnect', { method: 'POST' })\n" +
                "                .then(response => response.json())\n" +
                "                .then(data => {\n" +
                "                    if (data.success) {\n" +
                "                        showMessage(data.message || '已断开连接', false);\n" +
                "                    } else {\n" +
                "                        showMessage(data.message || '断开连接失败', true);\n" +
                "                    }\n" +
                "                })\n" +
                "                .catch(error => {\n" +
                "                    showMessage('断开连接失败: ' + error.message, true);\n" +
                "                });\n" +
                "        }\n" +
                "\n" +
                "        function togglePersistent() {\n" +
                "            fetch('/togglePersistent', { method: 'POST' })\n" +
                "                .then(response => response.json())\n" +
                "                .then(data => {\n" +
                "                    if (data.success) {\n" +
                "                        persistentConnection = data.persistentConnection;\n" +
                "                        showMessage(data.message || (persistentConnection ? '持久连接已启用' : '持久连接已禁用'), false);\n" +
                "                    }\n" +
                "                })\n" +
                "                .catch(error => {\n" +
                "                    showMessage('操作失败: ' + error.message, true);\n" +
                "                });\n" +
                "        }\n" +
                "\n" +
                "        // 允许按 Enter 键连接\n" +
                "        document.getElementById('serverAddress').addEventListener('keypress', function(e) {\n" +
                "            if (e.key === 'Enter') {\n" +
                "                connectToServer();\n" +
                "            }\n" +
                "        });\n" +
                "\n" +
                "        setInterval(updateStatus, 500);\n" +
                "        updateStatus();\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
    }

    /**
     * 发送响应
     */
    private static void sendResponse(HttpExchange exchange, String response, String contentType) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * 发送错误响应
     */
    private static void sendError(HttpExchange exchange, int code, String message) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(code, message.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(message.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 转义 JSON 字符串
     */
    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}

