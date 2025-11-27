package com.xiaofan.qqbot.handler;

import com.xiaofan.qqbot.websocket.SSTVWebSocketServer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * SSTV处理器
 * 处理/sstv命令，处理图片并通过WebSocket发送
 */
public class SSTVHandler {
    private static final Logger logger = LoggerFactory.getLogger(SSTVHandler.class);
    
    // 管理员QQ号
    private static final long ADMIN_QQ_ID = 2183576276L;
    
    private static final String TRIGGER_PREFIX = "/sstv";
    private static final String CANCEL_COOLDOWN_TRIGGER = "/sstv 取消冷却";
    private static final long COOLDOWN_MINUTES = 15; // 冷却时间15分钟
    
    private final BiFunction<Long, String, Boolean> messageSender;
    private final SSTVWebSocketServer webSocketServer;
    private final String tempImageDir;
    private final Map<Long, LocalDateTime> lastSuccessTime = new ConcurrentHashMap<>(); // 群号 -> 最后成功时间
    
    public SSTVHandler(BiFunction<Long, String, Boolean> messageSender,
                      SSTVWebSocketServer webSocketServer,
                      String tempImageDir) {
        this.messageSender = messageSender;
        this.webSocketServer = webSocketServer;
        this.tempImageDir = tempImageDir;
        
        // 确保临时目录存在
        File dir = new File(tempImageDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
    
    /**
     * 检查是否是管理员
     * @param userId 用户QQ号
     * @return 如果是管理员返回true，否则返回false
     */
    private boolean isAdmin(long userId) {
        return userId == ADMIN_QQ_ID;
    }
    
    /**
     * 检查是否应该处理消息
     * @param messageText 消息文本
     * @param event 消息事件（用于提取图片）
     * @return 如果应该处理返回true
     */
    public boolean shouldHandle(String messageText, JSONObject event) {
        if (messageText == null) {
            return false;
        }
        
        String trimmed = messageText.trim();
        
        // 检查是否是取消冷却命令
        if (trimmed.equals(CANCEL_COOLDOWN_TRIGGER)) {
            return true;
        }
        
        // 检查是否是/sstv命令（需要包含图片）
        if (event != null && trimmed.equals(TRIGGER_PREFIX)) {
            return hasImage(event);
        }
        
        return false;
    }
    
    /**
     * 检查消息中是否包含图片
     */
    private boolean hasImage(JSONObject event) {
        try {
            Object messageObj = event.opt("message");
            if (messageObj instanceof JSONArray) {
                JSONArray messageArray = (JSONArray) messageObj;
                for (int i = 0; i < messageArray.length(); i++) {
                    JSONObject segment = messageArray.getJSONObject(i);
                    String type = segment.optString("type", "");
                    if ("image".equals(type)) {
                        return true;
                    }
                }
            }
            
            // 检查raw_message中的CQ码
            String rawMessage = event.optString("raw_message", "");
            if (rawMessage.contains("[CQ:image")) {
                return true;
            }
        } catch (Exception e) {
            logger.warn("检查图片时发生错误", e);
        }
        
        return false;
    }
    
    /**
     * 处理SSTV命令
     * @param groupId 群号
     * @param userId 用户QQ号
     * @param messageText 消息文本
     * @param event 消息事件
     */
    public void handleSSTV(long groupId, long userId, String messageText, JSONObject event) {
        if (messageText == null) {
            messageText = "";
        }
        
        String trimmed = messageText.trim();
        
        // 处理取消冷却命令
        if (trimmed.equals(CANCEL_COOLDOWN_TRIGGER)) {
            handleCancelCooldown(groupId, userId);
            return;
        }
        
        // 检查冷却时间（管理员不受限制）
        if (!isAdmin(userId) && isInCooldown(groupId)) {
            long remainingMinutes = getRemainingCooldownMinutes(groupId);
            messageSender.apply(groupId, String.format("SSTV发射电台冷却中，%d分钟后发送", remainingMinutes));
            return;
        }
        
        // 回复"正在处理"
        messageSender.apply(groupId, "正在处理");
        
        // 提取图片URL
        String imageUrl = extractImageUrl(event);
        if (imageUrl == null || imageUrl.isEmpty()) {
            messageSender.apply(groupId, "未找到图片，请发送 /sstv + 一张图片");
            return;
        }
        
        logger.info("[SSTV] 收到处理请求，群号: {}, 用户: {}, 图片URL: {}", groupId, userId, imageUrl);
        
        // 下载图片
        String downloadedImagePath = downloadImage(imageUrl);
        if (downloadedImagePath == null) {
            messageSender.apply(groupId, "下载图片失败");
            return;
        }
        
        // 处理图片（使用ffmpeg）
        String processedImagePath = processImage(downloadedImagePath);
        if (processedImagePath == null) {
            messageSender.apply(groupId, "处理图片失败");
            return;
        }
        
        // 通过WebSocket发送
        boolean success = webSocketServer.sendImage(processedImagePath);
        
        if (success) {
            // 记录成功时间（管理员不受冷却限制，不记录）
            if (!isAdmin(userId)) {
                lastSuccessTime.put(groupId, LocalDateTime.now());
            }
            messageSender.apply(groupId, "处理完毕，已成功提交到发射电台");
            logger.info("[SSTV] 图片发送成功，群号: {}, 用户: {}", groupId, userId);
        } else {
            messageSender.apply(groupId, "提交失败！！发射电台离线");
            logger.warn("[SSTV] 图片发送失败，群号: {}", groupId);
        }
        
        // 清理临时文件
        cleanupTempFile(downloadedImagePath);
        if (!processedImagePath.equals(downloadedImagePath)) {
            cleanupTempFile(processedImagePath);
        }
    }
    
    /**
     * 处理取消冷却命令
     * @param groupId 群号
     * @param userId 用户QQ号
     */
    private void handleCancelCooldown(long groupId, long userId) {
        // 检查管理员权限
        if (!isAdmin(userId)) {
            logger.warn("非管理员尝试取消冷却，用户: {}", userId);
            messageSender.apply(groupId, "权限不足");
            return;
        }
        
        // 清除该群的冷却时间
        lastSuccessTime.remove(groupId);
        messageSender.apply(groupId, "已取消SSTV发射电台冷却");
        logger.info("[SSTV] 管理员 {} 取消了群 {} 的冷却", userId, groupId);
    }
    
    /**
     * 从消息事件中提取图片URL
     */
    private String extractImageUrl(JSONObject event) {
        try {
            Object messageObj = event.opt("message");
            if (messageObj instanceof JSONArray) {
                JSONArray messageArray = (JSONArray) messageObj;
                for (int i = 0; i < messageArray.length(); i++) {
                    JSONObject segment = messageArray.getJSONObject(i);
                    String type = segment.optString("type", "");
                    if ("image".equals(type)) {
                        JSONObject data = segment.optJSONObject("data");
                        if (data != null) {
                            // 尝试多种可能的字段名
                            String url = data.optString("url", "");
                            if (url.isEmpty()) {
                                url = data.optString("file", "");
                            }
                            if (url.isEmpty()) {
                                url = data.optString("file_id", "");
                            }
                            if (!url.isEmpty()) {
                                return url;
                            }
                        }
                    }
                }
            }
            
            // 从raw_message中提取CQ码
            String rawMessage = event.optString("raw_message", "");
            if (rawMessage.contains("[CQ:image")) {
                // 提取url=后面的内容
                int urlIndex = rawMessage.indexOf("url=");
                if (urlIndex != -1) {
                    int start = urlIndex + 4;
                    int end = rawMessage.indexOf(",", start);
                    if (end == -1) {
                        end = rawMessage.indexOf("]", start);
                    }
                    if (end != -1) {
                        return rawMessage.substring(start, end);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("提取图片URL时发生错误", e);
        }
        
        return null;
    }
    
    /**
     * 下载图片
     */
    private String downloadImage(String imageUrl) {
        try {
            // 使用OkHttp下载图片
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(imageUrl)
                    .build();
            
            try (okhttp3.Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.error("[SSTV] 下载图片失败，状态码: {}", response.code());
                    return null;
                }
                
                okhttp3.ResponseBody body = response.body();
                if (body == null) {
                    return null;
                }
                
                // 确定文件扩展名
                String extension = "jpg";
                String contentType = response.header("Content-Type", "");
                if (contentType.contains("png")) {
                    extension = "png";
                } else if (contentType.contains("gif")) {
                    extension = "gif";
                } else if (contentType.contains("webp")) {
                    extension = "webp";
                }
                
                // 保存到临时文件
                String filename = String.format("sstv_download_%d.%s", System.currentTimeMillis(), extension);
                String filePath = tempImageDir + File.separator + filename;
                
                Files.write(Paths.get(filePath), body.bytes());
                logger.info("[SSTV] 图片下载成功: {}", filePath);
                return filePath;
            }
        } catch (Exception e) {
            logger.error("[SSTV] 下载图片时发生异常", e);
            return null;
        }
    }
    
    /**
     * 使用ffmpeg处理图片
     * 横屏：转换为320x240
     * 竖屏：添加黑边转横屏，再转换为320x240
     */
    private String processImage(String inputPath) {
        try {
            // 先获取图片尺寸
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new File(inputPath));
            if (image == null) {
                logger.error("[SSTV] 无法读取图片: {}", inputPath);
                return null;
            }
            
            int width = image.getWidth();
            int height = image.getHeight();
            boolean isPortrait = height > width;
            
            String outputFilename = String.format("sstv_processed_%d.jpg", System.currentTimeMillis());
            String outputPath = tempImageDir + File.separator + outputFilename;
            
            // 构建ffmpeg命令
            ProcessBuilder pb;
            if (isPortrait) {
                // 竖屏：添加黑边转横屏，再转换为320x240
                // 计算需要的宽度（保持宽高比）
                int targetHeight = 240;
                int scaledWidth = (int) Math.round((double) width * targetHeight / height);
                
                // 如果缩放后的宽度小于320，需要添加黑边
                if (scaledWidth < 320) {
                    // 使用scale和pad滤镜
                    pb = new ProcessBuilder(
                            "ffmpeg", "-i", inputPath,
                            "-vf", String.format("scale=%d:%d,pad=320:240:(320-iw)/2:(240-ih)/2:black", scaledWidth, targetHeight),
                            "-y", outputPath
                    );
                } else {
                    // 如果缩放后宽度大于等于320，需要先缩放再裁剪
                    int scaledHeight = (int) Math.round((double) height * 320 / width);
                    pb = new ProcessBuilder(
                            "ffmpeg", "-i", inputPath,
                            "-vf", String.format("scale=320:%d,crop=320:240:0:(ih-240)/2", scaledHeight),
                            "-y", outputPath
                    );
                }
            } else {
                // 横屏：直接转换为320x240
                pb = new ProcessBuilder(
                        "ffmpeg", "-i", inputPath,
                        "-vf", "scale=320:240",
                        "-y", outputPath
                );
            }
            
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // 读取输出（避免缓冲区满）
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                logger.debug("[SSTV] ffmpeg: {}", line);
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.error("[SSTV] ffmpeg处理失败，退出码: {}", exitCode);
                return null;
            }
            
            if (!Files.exists(Paths.get(outputPath))) {
                logger.error("[SSTV] 处理后的图片文件不存在: {}", outputPath);
                return null;
            }
            
            logger.info("[SSTV] 图片处理成功: {} -> {}", inputPath, outputPath);
            return outputPath;
        } catch (Exception e) {
            logger.error("[SSTV] 处理图片时发生异常", e);
            return null;
        }
    }
    
    /**
     * 检查是否在冷却时间内
     */
    private boolean isInCooldown(long groupId) {
        LocalDateTime lastTime = lastSuccessTime.get(groupId);
        if (lastTime == null) {
            return false;
        }
        
        long minutes = ChronoUnit.MINUTES.between(lastTime, LocalDateTime.now());
        return minutes < COOLDOWN_MINUTES;
    }
    
    /**
     * 获取剩余冷却时间（分钟）
     */
    private long getRemainingCooldownMinutes(long groupId) {
        LocalDateTime lastTime = lastSuccessTime.get(groupId);
        if (lastTime == null) {
            return 0;
        }
        
        long minutes = ChronoUnit.MINUTES.between(lastTime, LocalDateTime.now());
        long remaining = COOLDOWN_MINUTES - minutes;
        return remaining > 0 ? remaining : 0;
    }
    
    /**
     * 清理临时文件
     */
    private void cleanupTempFile(String filePath) {
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            logger.warn("[SSTV] 清理临时文件失败: {}", filePath, e);
        }
    }
}
