package com.xiaofan.qqbot.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * SSTV WebSocket服务器
 * 在2024端口提供WebSocket服务，用于发送处理后的图片
 * 手动实现WebSocket协议
 */
public class SSTVWebSocketServer {
    private static final Logger logger = LoggerFactory.getLogger(SSTVWebSocketServer.class);
    
    private static final int WS_PORT = 2024;
    private static final String WS_MAGIC_STRING = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    
    private ServerSocket serverSocket;
    private final ConcurrentMap<String, ClientConnection> clients = new ConcurrentHashMap<>();
    private Consumer<Boolean> connectionStatusCallback;
    private volatile boolean isRunning = false;
    private ExecutorService executorService;
    
    public SSTVWebSocketServer() {
    }
    
    /**
     * 设置连接状态回调
     */
    public void setConnectionStatusCallback(Consumer<Boolean> callback) {
        this.connectionStatusCallback = callback;
    }
    
    /**
     * 启动WebSocket服务器
     */
    public void start() {
        if (isRunning) {
            logger.warn("[SSTV WebSocket] 服务器已在运行");
            return;
        }
        
        executorService = Executors.newCachedThreadPool();
        
        executorService.submit(() -> {
            try {
                serverSocket = new ServerSocket(WS_PORT);
                isRunning = true;
                logger.info("[SSTV WebSocket] 服务器已启动 (端口: {})", WS_PORT);
                notifyConnectionStatus();
                
                while (isRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        String clientId = clientSocket.getRemoteSocketAddress().toString();
                        ClientConnection client = new ClientConnection(clientId, clientSocket);
                        clients.put(clientId, client);
                        executorService.submit(client);
                        logger.info("[SSTV WebSocket] 客户端连接: {}", clientId);
                        notifyConnectionStatus();
                    } catch (IOException e) {
                        if (isRunning) {
                            logger.error("[SSTV WebSocket] 接受连接失败", e);
                        }
                    }
                }
            } catch (java.net.BindException e) {
                logger.error("[SSTV WebSocket] 启动失败：端口 {} 已被占用", WS_PORT);
                logger.error("[SSTV WebSocket] 可能的原因：");
                logger.error("[SSTV WebSocket] 1. 另一个QQbot实例正在运行");
                logger.error("[SSTV WebSocket] 2. 其他程序正在使用端口 {}", WS_PORT);
                logger.error("[SSTV WebSocket] 3. 之前的进程未正常关闭");
                logger.error("[SSTV WebSocket] 解决方案：");
                logger.error("[SSTV WebSocket] - 检查是否有其他QQbot进程：tasklist | findstr java");
                logger.error("[SSTV WebSocket] - 检查端口占用：netstat -ano | findstr :{}", WS_PORT);
                logger.error("[SSTV WebSocket] - 关闭占用端口的进程或重启系统");
                logger.error("[SSTV WebSocket] SSTV功能将无法使用，但机器人其他功能正常");
                isRunning = false;
            } catch (IOException e) {
                logger.error("[SSTV WebSocket] 启动失败", e);
                isRunning = false;
            }
        });
    }
    
    /**
     * 停止WebSocket服务器
     */
    public void stop() {
        isRunning = false;
        
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.error("[SSTV WebSocket] 关闭服务器失败", e);
            }
        }
        
        for (ClientConnection client : clients.values()) {
            client.close();
        }
        clients.clear();
        
        if (executorService != null) {
            executorService.shutdown();
        }
        
        logger.info("[SSTV WebSocket] 服务器已停止");
        notifyConnectionStatus();
    }
    
    /**
     * 发送图片数据
     */
    public boolean sendImage(String imagePath) {
        if (!isRunning) {
            logger.warn("[SSTV WebSocket] 服务器未运行，无法发送图片");
            return false;
        }
        
        if (clients.isEmpty()) {
            logger.warn("[SSTV WebSocket] 没有客户端连接，无法发送图片");
            return false;
        }
        
        try {
            if (!Files.exists(Paths.get(imagePath))) {
                logger.error("[SSTV WebSocket] 图片文件不存在: {}", imagePath);
                return false;
            }
            
            byte[] imageData = Files.readAllBytes(Paths.get(imagePath));
            
            int successCount = 0;
            for (ClientConnection client : clients.values()) {
                try {
                    if (client.isOpen() && client.send(imageData)) {
                        successCount++;
                        logger.debug("[SSTV WebSocket] 图片已发送到客户端: {}", client.getId());
                    }
                } catch (Exception e) {
                    logger.warn("[SSTV WebSocket] 发送图片到客户端失败: {}", client.getId(), e);
                    clients.remove(client.getId());
                }
            }
            
            if (successCount > 0) {
                logger.info("[SSTV WebSocket] 图片发送成功，共 {} 个客户端", successCount);
                notifyConnectionStatus();
                return true;
            } else {
                logger.warn("[SSTV WebSocket] 所有客户端连接已关闭");
                notifyConnectionStatus();
                return false;
            }
        } catch (IOException e) {
            logger.error("[SSTV WebSocket] 读取图片文件失败: {}", imagePath, e);
            return false;
        }
    }
    
    /**
     * 检查是否有客户端连接
     */
    public boolean hasClients() {
        if (!isRunning) {
            return false;
        }
        clients.entrySet().removeIf(entry -> !entry.getValue().isOpen());
        return !clients.isEmpty();
    }
    
    /**
     * 检查服务器是否正在运行
     */
    public boolean isServerRunning() {
        return isRunning;
    }
    
    /**
     * 通知连接状态变化
     */
    private void notifyConnectionStatus() {
        if (connectionStatusCallback != null) {
            connectionStatusCallback.accept(hasClients());
        }
    }
    
    /**
     * 客户端连接处理
     */
    private class ClientConnection implements Runnable {
        private final String id;
        private final Socket socket;
        private BufferedReader reader;
        private OutputStream writer;
        private volatile boolean open = false;
        
        public ClientConnection(String id, Socket socket) {
            this.id = id;
            this.socket = socket;
        }
        
        @Override
        public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                writer = socket.getOutputStream();
                
                // 处理WebSocket握手
                if (performHandshake()) {
                    open = true;
                    logger.info("[SSTV WebSocket] 握手成功: {}", id);
                    
                    // 保持连接，读取消息（可选）
                    keepAlive();
                } else {
                    logger.warn("[SSTV WebSocket] 握手失败: {}", id);
                }
            } catch (Exception e) {
                logger.error("[SSTV WebSocket] 客户端连接错误: {}", id, e);
            } finally {
                close();
            }
        }
        
        /**
         * 执行WebSocket握手
         */
        private boolean performHandshake() throws IOException {
            StringBuilder request = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                request.append(line).append("\r\n");
            }
            
            String requestStr = request.toString();
            if (!requestStr.contains("Upgrade: websocket")) {
                return false;
            }
            
            // 提取Sec-WebSocket-Key
            String key = extractWebSocketKey(requestStr);
            if (key == null) {
                return false;
            }
            
            // 生成响应key
            String acceptKey = generateAcceptKey(key);
            
            // 发送握手响应
            String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                            "Upgrade: websocket\r\n" +
                            "Connection: Upgrade\r\n" +
                            "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";
            
            writer.write(response.getBytes());
            writer.flush();
            
            return true;
        }
        
        /**
         * 提取WebSocket Key
         */
        private String extractWebSocketKey(String request) {
            String[] lines = request.split("\r\n");
            for (String line : lines) {
                if (line.startsWith("Sec-WebSocket-Key:")) {
                    return line.substring("Sec-WebSocket-Key:".length()).trim();
                }
            }
            return null;
        }
        
        /**
         * 生成Accept Key
         */
        private String generateAcceptKey(String key) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                String input = key + WS_MAGIC_STRING;
                byte[] hash = md.digest(input.getBytes("UTF-8"));
                return Base64.getEncoder().encodeToString(hash);
            } catch (Exception e) {
                logger.error("生成Accept Key失败", e);
                return null;
            }
        }
        
        /**
         * 保持连接
         */
        private void keepAlive() {
            try {
                // 简单读取，保持连接
                while (open && socket.isConnected() && !socket.isClosed()) {
                    int b = socket.getInputStream().read();
                    if (b == -1) {
                        break;
                    }
                    // 可以处理ping/pong帧
                }
            } catch (IOException e) {
                // 连接关闭
            }
        }
        
        /**
         * 发送数据（WebSocket帧格式）
         */
        public boolean send(byte[] data) {
            if (!open || socket.isClosed()) {
                return false;
            }
            
            try {
                // 构建WebSocket帧
                ByteArrayOutputStream frame = new ByteArrayOutputStream();
                
                // FIN=1, opcode=2 (binary)
                frame.write(0x82);
                
                // 掩码=0（服务器发送不需要掩码）
                if (data.length < 126) {
                    frame.write(data.length);
                } else if (data.length < 65536) {
                    frame.write(126);
                    frame.write((data.length >> 8) & 0xFF);
                    frame.write(data.length & 0xFF);
                } else {
                    frame.write(127);
                    for (int i = 7; i >= 0; i--) {
                        frame.write((int)((data.length >> (i * 8)) & 0xFF));
                    }
                }
                
                frame.write(data);
                
                synchronized (writer) {
                    writer.write(frame.toByteArray());
                    writer.flush();
                }
                
                return true;
            } catch (IOException e) {
                logger.error("[SSTV WebSocket] 发送数据失败: {}", id, e);
                return false;
            }
        }
        
        public String getId() {
            return id;
        }
        
        public boolean isOpen() {
            return open && socket.isConnected() && !socket.isClosed();
        }
        
        public void close() {
            open = false;
            try {
                if (reader != null) reader.close();
                if (writer != null) writer.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                // 忽略
            }
            clients.remove(id);
            logger.info("[SSTV WebSocket] 客户端断开: {}", id);
            notifyConnectionStatus();
        }
    }
}
