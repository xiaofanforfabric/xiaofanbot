package com.xiaofan.qqbot.websocket;

import org.json.JSONObject;
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
 * KOOK回复消息WebSocket服务器
 * 在8849端口提供WebSocket服务，接收QQbot的回复消息并转发给KOOKbot
 */
public class KookReplyServer {
    private static final Logger logger = LoggerFactory.getLogger(KookReplyServer.class);
    
    private static final int WS_PORT = 8849;
    private static final String WS_MAGIC_STRING = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    
    private ServerSocket serverSocket;
    private final ConcurrentMap<String, ClientConnection> clients = new ConcurrentHashMap<>();
    private volatile boolean isRunning = false;
    private ExecutorService executorService;
    private ClientConnection kookBotConnection; // KOOKbot的连接
    
    /**
     * 启动WebSocket服务器
     */
    public void start() {
        if (isRunning) {
            logger.warn("[KOOK回复服务器] 服务器已在运行");
            return;
        }
        
        executorService = Executors.newCachedThreadPool();
        
        executorService.submit(() -> {
            try {
                serverSocket = new ServerSocket(WS_PORT);
                isRunning = true;
                logger.info("[KOOK回复服务器] 服务器已启动 (端口: {})", WS_PORT);
                
                while (isRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        String clientId = clientSocket.getRemoteSocketAddress().toString();
                        ClientConnection client = new ClientConnection(clientId, clientSocket);
                        clients.put(clientId, client);
                        executorService.submit(client);
                        logger.info("[KOOK回复服务器] 客户端连接: {}", clientId);
                    } catch (IOException e) {
                        if (isRunning) {
                            logger.error("[KOOK回复服务器] 接受连接失败", e);
                        }
                    }
                }
            } catch (java.net.BindException e) {
                logger.error("[KOOK回复服务器] 启动失败：端口 {} 已被占用", WS_PORT);
            } catch (IOException e) {
                if (isRunning) {
                    logger.error("[KOOK回复服务器] 服务器运行错误", e);
                }
            } finally {
                isRunning = false;
                logger.info("[KOOK回复服务器] 服务器已停止");
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
                logger.error("[KOOK回复服务器] 关闭服务器失败", e);
            }
        }
        if (executorService != null) {
            executorService.shutdown();
        }
        // 关闭所有客户端连接
        for (ClientConnection client : clients.values()) {
            client.close();
        }
        clients.clear();
    }
    
    /**
     * 发送回复消息到KOOKbot
     * @param channelId KOOK频道ID
     * @param message 消息内容
     * @return 是否发送成功
     */
    public boolean sendReply(String channelId, String message) {
        if (kookBotConnection == null || !kookBotConnection.isOpen()) {
            logger.warn("[KOOK回复服务器] KOOKbot未连接，无法发送回复消息");
            return false;
        }
        
        try {
            JSONObject reply = new JSONObject();
            reply.put("channel_id", channelId);
            reply.put("message", message);
            
            String replyJson = reply.toString();
            boolean sent = kookBotConnection.sendMessage(replyJson);
            if (sent) {
                logger.debug("[KOOK回复服务器] 回复消息已发送: channelId={}, message={}", channelId, message);
            } else {
                logger.warn("[KOOK回复服务器] 回复消息发送失败: channelId={}", channelId);
            }
            return sent;
        } catch (Exception e) {
            logger.error("[KOOK回复服务器] 发送回复消息时发生错误", e);
            return false;
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
                    // 记录为KOOKbot连接
                    kookBotConnection = this;
                    logger.info("[KOOK回复服务器] KOOKbot已连接: {}", id);
                    
                    // 握手成功后，保持连接打开，等待接收消息
                    // 注意：这里不需要读取消息，因为服务器只需要发送消息给客户端
                    // 客户端（KOOKbot）会通过OkHttp的WebSocket监听接收消息
                    // 我们只需要保持连接打开即可
                    try {
                        // 保持连接打开，等待客户端关闭或服务器关闭
                        while (open && !socket.isClosed()) {
                            Thread.sleep(1000); // 每秒检查一次连接状态
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.debug("[KOOK回复服务器] 连接保持循环被中断");
                    }
                } else {
                    logger.warn("[KOOK回复服务器] 握手失败: {}", id);
                }
            } catch (Exception e) {
                logger.error("[KOOK回复服务器] 客户端连接错误: {}", id, e);
            } finally {
                if (kookBotConnection == this) {
                    kookBotConnection = null;
                }
                close();
            }
        }
        
        /**
         * 执行WebSocket握手
         */
        private boolean performHandshake() throws IOException {
            String line = reader.readLine();
            if (line == null || !line.startsWith("GET")) {
                return false;
            }
            
            // 读取所有请求头
            StringBuilder headers = new StringBuilder();
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                headers.append(line).append("\r\n");
            }
            
            // 提取Sec-WebSocket-Key
            String key = null;
            String[] headerLines = headers.toString().split("\r\n");
            for (String header : headerLines) {
                if (header.startsWith("Sec-WebSocket-Key:")) {
                    key = header.substring("Sec-WebSocket-Key:".length()).trim();
                    break;
                }
            }
            
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
            
            writer.write(response.getBytes("UTF-8"));
            writer.flush();
            
            return true;
        }
        
        /**
         * 生成WebSocket Accept Key
         */
        private String generateAcceptKey(String key) throws IOException {
            try {
                String combined = key + WS_MAGIC_STRING;
                MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
                byte[] hash = sha1.digest(combined.getBytes("UTF-8"));
                return Base64.getEncoder().encodeToString(hash);
            } catch (Exception e) {
                throw new IOException("生成Accept Key失败", e);
            }
        }
        
        /**
         * 发送WebSocket消息
         */
        public boolean sendMessage(String message) {
            if (!open) {
                return false;
            }
            
            try {
                byte[] messageBytes = message.getBytes("UTF-8");
                int messageLength = messageBytes.length;
                
                // 构建WebSocket帧
                ByteArrayOutputStream frame = new ByteArrayOutputStream();
                
                // 第一个字节：FIN=1, RSV=0, Opcode=0x01(文本帧)
                frame.write(0x81);
                
                // 第二个字节：MASK=0(服务器发送不需要掩码), Payload长度
                if (messageLength < 126) {
                    frame.write(messageLength);
                } else if (messageLength < 65536) {
                    frame.write(126);
                    frame.write((messageLength >> 8) & 0xFF);
                    frame.write(messageLength & 0xFF);
                } else {
                    frame.write(127);
                    for (int i = 7; i >= 0; i--) {
                        frame.write((int)((messageLength >> (i * 8)) & 0xFF));
                    }
                }
                
                // 写入消息数据
                frame.write(messageBytes);
                
                // 发送帧
                writer.write(frame.toByteArray());
                writer.flush();
                
                return true;
            } catch (IOException e) {
                logger.error("[KOOK回复服务器] 发送消息失败", e);
                open = false;
                return false;
            }
        }
        
        public boolean isOpen() {
            return open;
        }
        
        public String getId() {
            return id;
        }
        
        public void close() {
            open = false;
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                logger.debug("[KOOK回复服务器] 关闭连接失败", e);
            }
            clients.remove(id);
            logger.info("[KOOK回复服务器] 客户端断开: {}", id);
        }
    }
}
