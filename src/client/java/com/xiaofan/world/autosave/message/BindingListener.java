package com.xiaofan.world.autosave.message;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 绑定消息监听器
 * 监听聊天框中的"绑定"消息，提取验证码并调用绑定API
 */
public class BindingListener {
    private static final String BINDING_API_URL = "http://127.0.0.1:2001/pass_code";
    // 匹配格式: "玩家名: 绑定验证码" 或 "玩家名:绑定验证码"
    // 示例: "debug_player: 绑定114514" 或 "xiaofan:绑定123456"
    private static final Pattern BINDING_PATTERN = Pattern.compile("^(.+?):\\s*绑定(\\d{6})$");
    
    /**
     * 初始化绑定监听器
     */
    public static void initialize() {
        // 监听GAME事件
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String messageText = message.getString();
            if (messageText != null && !messageText.trim().isEmpty()) {
                processMessage(messageText);
            }
        });
        
        // 监听CHAT事件
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            String messageText = message.getString();
            if (messageText != null && !messageText.trim().isEmpty()) {
                processMessage(messageText);
            }
        });
        
        // 监听ALLOW_CHAT事件
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            String messageText = message.getString();
            if (messageText != null && !messageText.trim().isEmpty()) {
                processMessage(messageText);
            }
            return true;
        });
        
        System.out.println("[绑定监听器] 已初始化");
    }
    
    /**
     * 处理消息
     * @param rawMessage 原始消息
     */
    private static void processMessage(String rawMessage) {
        // 过滤系统消息
        if (isSystemMessage(rawMessage)) {
            return;
        }
        
        // 去掉 [System] [CHAT] 前缀
        String cleanMessage = removeSystemPrefix(rawMessage);
        
        // 检查消息是否匹配自定义格式（必须有方括号前缀）
        if (!hasBracketFormat(cleanMessage)) {
            return;
        }
        
        // 解析自定义消息格式，提取实际的聊天内容
        String parsedMessage = parseCustomMessageFormat(cleanMessage);
        
        if (parsedMessage == null || parsedMessage.trim().isEmpty()) {
            return;
        }
        
        // 检查是否是绑定消息
        Matcher matcher = BINDING_PATTERN.matcher(parsedMessage.trim());
        if (matcher.matches()) {
            String gameId = matcher.group(1).trim();
            String passCode = matcher.group(2);
            
            System.out.println("[绑定监听器] 检测到绑定消息: game_id=" + gameId + ", pass_code=" + passCode);
            
            // 发送绑定请求
            sendBindingRequest(gameId, passCode);
        }
    }
    
    /**
     * 发送绑定请求到API
     * @param gameId 游戏ID（玩家名）
     * @param passCode 验证码
     */
    private static void sendBindingRequest(String gameId, String passCode) {
        // 先发送"正在请求绑定"命令
        sendCommandToServer("/c 正在请求绑定");
        
        // 异步发送API请求
        new Thread(() -> {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                // 构建请求体
                JSONObject requestJson = new JSONObject();
                requestJson.put("game_id", gameId);
                requestJson.put("pass_code", passCode);
                
                HttpPost httpPost = new HttpPost(BINDING_API_URL);
                httpPost.setHeader("Content-Type", "application/json; charset=utf-8");
                httpPost.setEntity(new StringEntity(requestJson.toString(), StandardCharsets.UTF_8));
                
                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    
                    if (statusCode == 200) {
                        // 读取响应
                        String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        
                        String end = jsonResponse.optString("end", "");
                        
                        if ("".equals(end)) {
                            // 绑定成功
                            System.out.println("[绑定监听器] 绑定成功: game_id=" + gameId);
                            sendCommandToServer("/c 请求绑定成功");
                        } else {
                            // 绑定失败
                            String error = jsonResponse.optString("error", "未知错误");
                            System.out.println("[绑定监听器] 绑定失败: " + error);
                            sendCommandToServer("/c 请求发送失败");
                        }
                    } else {
                        // HTTP状态码不是200
                        System.out.println("[绑定监听器] API请求失败，状态码: " + statusCode);
                        sendCommandToServer("/c 请求发送失败");
                    }
                }
            } catch (IOException e) {
                System.err.println("[绑定监听器] 发送绑定请求时发生IO异常: " + e.getMessage());
                e.printStackTrace();
                sendCommandToServer("/c 请求发送失败");
            } catch (Exception e) {
                System.err.println("[绑定监听器] 发送绑定请求时发生异常: " + e.getMessage());
                e.printStackTrace();
                sendCommandToServer("/c 请求发送失败");
            }
        }).start();
    }
    
    /**
     * 发送命令到服务器
     * @param command 命令（包含/c前缀，如：/c 消息内容）
     */
    private static void sendCommandToServer(String command) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null && client.getNetworkHandler() != null) {
            // 处理命令格式
            // sendCommand方法会自动添加/前缀，所以：
            // - 如果命令是 "/c 消息"，应该传入 "c 消息"，会变成 "/c 消息"
            // - 如果命令是 "/消息"，应该传入 "消息"，会变成 "/消息"
            String cleanCommand;
            String trimmedCommand = command.trim();
            
            if (trimmedCommand.startsWith("/c ")) {
                // 去掉第一个 "/"，保留 "c 消息内容"
                cleanCommand = trimmedCommand.substring(1); // "/c 消息" -> "c 消息"
                System.out.println("[绑定监听器] 检测到/c命令，原始: " + command + ", 处理后: " + cleanCommand);
            } else if (trimmedCommand.startsWith("/")) {
                // 去掉 "/" 前缀
                cleanCommand = trimmedCommand.substring(1);
                System.out.println("[绑定监听器] 检测到/命令，原始: " + command + ", 处理后: " + cleanCommand);
            } else {
                cleanCommand = trimmedCommand;
                System.out.println("[绑定监听器] 无前缀命令，原始: " + command + ", 处理后: " + cleanCommand);
            }
            
            client.execute(() -> {
                try {
                    // sendCommand会自动添加/前缀
                    // 例如：sendCommand("c 请求发送失败") -> "/c 请求发送失败"
                    client.getNetworkHandler().sendCommand(cleanCommand);
                    System.out.println("[绑定监听器] 已发送命令: /" + cleanCommand);
                } catch (Exception e) {
                    System.err.println("[绑定监听器] 发送命令失败: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }
    }
    
    /**
     * 判断是否是系统消息（需要过滤掉的消息）
     */
    private static boolean isSystemMessage(String message) {
        if (message == null) {
            return true;
        }
        
        String lower = message.toLowerCase();
        
        return lower.contains("加入了游戏") ||
               lower.contains("离开服务器") ||
               lower.contains("进入了服务器") ||
               lower.contains("退出了服务器") ||
               lower.contains("joined the game") ||
               lower.contains("left the game");
    }
    
    /**
     * 去掉消息中的 [System] [CHAT] 前缀
     */
    private static String removeSystemPrefix(String message) {
        if (message == null) {
            return null;
        }
        
        String cleaned = message;
        
        // 匹配 [System] [CHAT] 前缀并去掉
        Pattern pattern = Pattern.compile("^\\s*\\[System\\]\\s*\\[CHAT\\]\\s*(.+)$", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(cleaned);
        if (matcher.matches()) {
            cleaned = matcher.group(1).trim();
        }
        
        // 匹配 [System] 前缀并去掉
        pattern = Pattern.compile("^\\s*\\[System\\]\\s*(.+)$", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(cleaned);
        if (matcher.matches()) {
            cleaned = matcher.group(1).trim();
        }
        
        return cleaned;
    }
    
    /**
     * 判断消息是否包含方括号格式
     */
    private static boolean hasBracketFormat(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = message.trim();
        Pattern bracketPattern = Pattern.compile("\\[[^\\]]+\\]");
        return bracketPattern.matcher(trimmed).find();
    }
    
    /**
     * 解析自定义消息格式，提取实际的聊天内容
     * 与mes.java中的parseCustomMessageFormat逻辑相同
     */
    private static String parseCustomMessageFormat(String rawMessage) {
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = rawMessage.trim();
        
        // 格式1: [xxx] [xxx] [xxx] 实际消息内容（三个方括号）
        Pattern pattern = Pattern.compile(
            "^\\s*\\[([^\\]]+)\\]\\s*\\[([^\\]]+)\\]\\s*\\[([^\\]]+)\\]\\s*(.+)$"
        );
        
        Matcher matcher = pattern.matcher(trimmed);
        if (matcher.matches()) {
            String chatContent = matcher.group(4).trim();
            System.out.println("[绑定监听器] ✓ 匹配三括号格式 -> " + chatContent);
            return chatContent;
        }
        
        // 格式2: [xxx] [xxx] 实际消息内容（两个方括号）
        pattern = Pattern.compile(
            "^\\s*\\[([^\\]]+)\\]\\s*\\[([^\\]]+)\\]\\s*(.+)$"
        );
        matcher = pattern.matcher(trimmed);
        if (matcher.matches()) {
            String chatContent = matcher.group(3).trim();
            System.out.println("[绑定监听器] ✓ 匹配两括号格式 -> " + chatContent);
            return chatContent;
        }
        
        // 格式3: [xxx] 实际消息内容（一个方括号）
        pattern = Pattern.compile(
            "^\\s*\\[([^\\]]+)\\]\\s*(.+)$"
        );
        matcher = pattern.matcher(trimmed);
        if (matcher.matches()) {
            String chatContent = matcher.group(2).trim();
            System.out.println("[绑定监听器] ✓ 匹配单括号格式 -> " + chatContent);
            return chatContent;
        }
        
        return null;
    }
}

