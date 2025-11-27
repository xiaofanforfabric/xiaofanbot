package com.xiaofan.qqbot.websocket;

import com.xiaofan.qqbot.QQBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * KOOK消息转发WebSocket服务器
 * 在8848端口提供WebSocket服务，接收KOOK消息并转发给QQBot处理
 */
public class KookMessageForwardServer {
    private static final Logger logger = LoggerFactory.getLogger(KookMessageForwardServer.class);
    
    private static final int WS_PORT = 8848;
    private static final String WS_MAGIC_STRING = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    
    private ServerSocket serverSocket;
    private final ConcurrentMap<String, ClientConnection> clients = new ConcurrentHashMap<>();
    private volatile boolean isRunning = false;
    private ExecutorService executorService;
    private QQBot.MessageHandler messageHandler;
    
    public KookMessageForwardServer(QQBot.MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }
    
    /**
     * 启动WebSocket服务器
     */
    public void start() {
        if (isRunning) {
            logger.warn("[KOOK转发服务器] 服务器已在运行");
            return;
        }
        
        executorService = Executors.newCachedThreadPool();
        
        executorService.submit(() -> {
            try {
                serverSocket = new ServerSocket(WS_PORT);
                isRunning = true;
                logger.info("[KOOK转发服务器] 服务器已启动 (端口: {})", WS_PORT);
                
                while (isRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        String clientId = clientSocket.getRemoteSocketAddress().toString();
                        ClientConnection client = new ClientConnection(clientId, clientSocket);
                        clients.put(clientId, client);
                        executorService.submit(client);
                        logger.info("[KOOK转发服务器] 客户端连接: {}", clientId);
                    } catch (IOException e) {
                        if (isRunning) {
                            logger.error("[KOOK转发服务器] 接受连接失败", e);
                        }
                    }
                }
            } catch (java.net.BindException e) {
                logger.error("[KOOK转发服务器] 启动失败：端口 {} 已被占用", WS_PORT);
                logger.error("[KOOK转发服务器] 可能的原因：");
                logger.error("[KOOK转发服务器] 1. 另一个QQbot实例正在运行");
                logger.error("[KOOK转发服务器] 2. 其他程序正在使用端口 {}", WS_PORT);
                logger.error("[KOOK转发服务器] 3. 之前的进程未正常关闭");
                logger.error("[KOOK转发服务器] 解决方案：");
                logger.error("[KOOK转发服务器] - 检查是否有其他QQbot进程：tasklist | findstr java");
                logger.error("[KOOK转发服务器] - 检查端口占用：netstat -ano | findstr :{}", WS_PORT);
                logger.error("[KOOK转发服务器] - 关闭占用端口的进程或重启系统");
                logger.error("[KOOK转发服务器] KOOK消息转发功能将无法使用，但机器人其他功能正常");
                isRunning = false;
            } catch (IOException e) {
                logger.error("[KOOK转发服务器] 启动失败", e);
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
                logger.error("[KOOK转发服务器] 关闭服务器失败", e);
            }
        }
        
        for (ClientConnection client : clients.values()) {
            client.close();
        }
        clients.clear();
        
        if (executorService != null) {
            executorService.shutdown();
        }
        
        logger.info("[KOOK转发服务器] 服务器已停止");
    }
    
    /**
     * 检查服务器是否正在运行
     */
    public boolean isServerRunning() {
        return isRunning;
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
                    logger.info("[KOOK转发服务器] 握手成功: {}", id);
                    
                    // 读取并处理消息
                    readMessages();
                } else {
                    logger.warn("[KOOK转发服务器] 握手失败: {}", id);
                }
            } catch (Exception e) {
                logger.error("[KOOK转发服务器] 客户端连接错误: {}", id, e);
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
         * 读取并处理消息
         */
        private void readMessages() {
            try {
                InputStream inputStream = socket.getInputStream();
                while (open && socket.isConnected() && !socket.isClosed()) {
                    // 读取WebSocket帧
                    int firstByte = inputStream.read();
                    if (firstByte == -1) {
                        break;
                    }
                    
                    boolean fin = (firstByte & 0x80) != 0;
                    int opcode = firstByte & 0x0F;
                    
                    int secondByte = inputStream.read();
                    if (secondByte == -1) {
                        break;
                    }
                    
                    boolean masked = (secondByte & 0x80) != 0;
                    int payloadLength = secondByte & 0x7F;
                    
                    // 读取扩展长度
                    if (payloadLength == 126) {
                        payloadLength = (inputStream.read() << 8) | inputStream.read();
                    } else if (payloadLength == 127) {
                        // 64位长度，跳过
                        for (int i = 0; i < 8; i++) {
                            inputStream.read();
                        }
                        payloadLength = 0; // 简化处理，不支持超大消息
                    }
                    
                    // 读取掩码（客户端发送的消息有掩码）
                    byte[] mask = new byte[4];
                    if (masked) {
                        inputStream.read(mask);
                    }
                    
                    // 读取数据
                    byte[] payload = new byte[payloadLength];
                    inputStream.read(payload);
                    
                    // 解掩码
                    if (masked) {
                        for (int i = 0; i < payload.length; i++) {
                            payload[i] ^= mask[i % 4];
                        }
                    }
                    
                    // 处理文本消息
                    if (opcode == 0x01) { // 文本帧
                        String message = new String(payload, "UTF-8");
                        logger.debug("[KOOK转发服务器] 收到消息: {}", message);
                        
                        // 转发给QQBot的MessageHandler处理
                        if (messageHandler != null) {
                            try {
                                messageHandler.handleWebSocketMessage(message);
                                logger.debug("[KOOK转发服务器] 消息已转发给QQBot处理");
                            } catch (Exception e) {
                                logger.error("[KOOK转发服务器] 转发消息失败", e);
                            }
                        }
                    } else if (opcode == 0x08) { // 关闭帧
                        logger.info("[KOOK转发服务器] 收到关闭帧");
                        break;
                    } else if (opcode == 0x09) { // Ping帧
                        // 回复Pong
                        sendPong();
                    } else if (opcode == 0x0A) { // Pong帧
                        logger.debug("[KOOK转发服务器] 收到Pong");
                    }
                }
            } catch (IOException e) {
                if (open) {
                    logger.debug("[KOOK转发服务器] 读取消息时连接关闭: {}", id);
                }
            }
        }
        
        /**
         * 发送Pong响应
         */
        private void sendPong() {
            try {
                byte[] frame = new byte[2];
                frame[0] = (byte) 0x8A; // FIN=1, opcode=0x0A (Pong)
                frame[1] = 0x00; // 无负载
                synchronized (writer) {
                    writer.write(frame);
                    writer.flush();
                }
            } catch (IOException e) {
                logger.debug("[KOOK转发服务器] 发送Pong失败", e);
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
            logger.info("[KOOK转发服务器] 客户端断开: {}", id);
        }
    }
}
