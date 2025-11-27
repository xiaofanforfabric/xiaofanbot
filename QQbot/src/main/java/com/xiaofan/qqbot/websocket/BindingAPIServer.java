package com.xiaofan.qqbot.websocket;

import com.xiaofan.qqbot.manager.BindingSessionManager;
import com.xiaofan.qqbot.manager.DatabaseManager;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

/**
 * 绑定API服务器
 * 在2001端口提供/pass_code API，接收游戏验证码
 */
public class BindingAPIServer {
    private static final Logger logger = LoggerFactory.getLogger(BindingAPIServer.class);
    
    private static final int HTTP_PORT = 2001;
    private static HttpServer httpServer;
    private static volatile boolean isRunning = false;
    private static volatile boolean isEnabled = false; // API是否启用（仅在绑定会话期间启用）
    
    private final BindingSessionManager sessionManager;
    private final DatabaseManager databaseManager;
    private BiConsumer<Long, Long> bindingSuccessCallback; // 绑定成功回调 (groupId, qqId)
    
    public BindingAPIServer(BindingSessionManager sessionManager, 
                           DatabaseManager databaseManager) {
        this.sessionManager = sessionManager;
        this.databaseManager = databaseManager;
    }
    
    /**
     * 设置绑定成功回调
     */
    public void setBindingSuccessCallback(BiConsumer<Long, Long> callback) {
        this.bindingSuccessCallback = callback;
    }
    
    /**
     * 初始化HTTP服务器
     */
    public void initialize() {
        if (isRunning) {
            return;
        }
        
        try {
            httpServer = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
            httpServer.createContext("/pass_code", new PassCodeHandler());
            httpServer.setExecutor(Executors.newCachedThreadPool());
            httpServer.start();
            isRunning = true;
            
            logger.info("[绑定API] HTTP服务器已启动 (端口: {})", HTTP_PORT);
        } catch (IOException e) {
            logger.error("[绑定API] 启动失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 启用API（在绑定会话开始时调用）
     */
    public void enable() {
        isEnabled = true;
        logger.info("[绑定API] API已启用，等待验证码");
    }
    
    /**
     * 禁用API（在绑定会话结束时调用）
     */
    public void disable() {
        isEnabled = false;
        logger.info("[绑定API] API已禁用");
    }
    
    /**
     * 检查服务器是否正在运行
     * @return 如果服务器正在运行返回true，否则返回false
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * 停止HTTP服务器
     */
    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
            isRunning = false;
            isEnabled = false;
            logger.info("[绑定API] HTTP服务器已停止");
        }
    }
    
    /**
     * 处理/pass_code请求
     */
    private class PassCodeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 只接受POST请求
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "Method Not Allowed");
                return;
            }
            
            // 如果API未启用，返回503
            if (!isEnabled) {
                sendErrorResponse(exchange, 503, "Service Unavailable");
                return;
            }
            
            try {
                // 读取请求体
                InputStream requestBody = exchange.getRequestBody();
                String requestText = new String(requestBody.readAllBytes(), StandardCharsets.UTF_8);
                
                logger.info("[绑定API] 收到请求: {}", requestText);
                
                // 解析JSON
                JSONObject requestJson = new JSONObject(requestText);
                String gameId = requestJson.optString("game_id", "");
                String passCode = requestJson.optString("pass_code", "");
                
                // 验证必填字段
                if (gameId.isEmpty() || passCode.isEmpty()) {
                    logger.warn("[绑定API] 请求缺少必填字段: game_id={}, pass_code={}", gameId, passCode);
                    sendErrorResponse(exchange, 400, "Missing required fields: game_id or pass_code");
                    return;
                }
                
                // 验证验证码
                BindingSessionManager.BindingSession session = sessionManager.verifyAndGetSession(passCode);
                
                if (session == null) {
                    logger.warn("[绑定API] 验证码验证失败: {}", passCode);
                    sendErrorResponse(exchange, 400, "Invalid pass_code");
                    return;
                }
                
                // 更新数据库
                boolean success = databaseManager.updateGameId(session.qqId, gameId);
                
                if (success) {
                    // 发送成功响应
                    JSONObject response = new JSONObject();
                    response.put("end", "");
                    sendSuccessResponse(exchange, response);
                    
                    // 通知绑定成功回调
                    if (bindingSuccessCallback != null) {
                        bindingSuccessCallback.accept(session.groupId, session.qqId);
                    }
                    
                    // 结束会话
                    sessionManager.endSession();
                    disable();
                    
                    logger.info("[绑定API] 绑定成功，QQ号: {}, game_id: {}", session.qqId, gameId);
                } else {
                    logger.error("[绑定API] 数据库更新失败，QQ号: {}", session.qqId);
                    sendErrorResponse(exchange, 500, "Database update failed");
                }
                
            } catch (Exception e) {
                logger.error("[绑定API] 处理请求时发生异常", e);
                sendErrorResponse(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }
        
        private void sendSuccessResponse(HttpExchange exchange, JSONObject response) throws IOException {
            String responseText = response.toString();
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, responseText.getBytes(StandardCharsets.UTF_8).length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseText.getBytes(StandardCharsets.UTF_8));
            }
        }
        
        private void sendErrorResponse(HttpExchange exchange, int statusCode, String error) throws IOException {
            JSONObject response = new JSONObject();
            response.put("end", "error");
            response.put("error", error);
            
            String responseText = response.toString();
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(statusCode, responseText.getBytes(StandardCharsets.UTF_8).length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseText.getBytes(StandardCharsets.UTF_8));
            }
            
            logger.warn("[绑定API] 返回错误响应: {} - {}", statusCode, error);
        }
    }
}
