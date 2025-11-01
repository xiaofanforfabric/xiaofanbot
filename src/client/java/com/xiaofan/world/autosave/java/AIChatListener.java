package com.xiaofan.world.autosave.java;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AIChatListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("AIChatListener");

    // 多个触发词配置
    private static final String TRIGGER_PREFIX = "xiaofanchat";
    private static final String TRIGGER_PREFIX2 = "wans2023";
    private static final String TRIGGER_PREFIX3 = "test";

    // 频率限制配置
    private static final int RATE_LIMIT_MAX_REQUESTS = 10; // 每分钟最多10次请求
    private static final long RATE_LIMIT_TIME_WINDOW = 60000; // 1分钟（毫秒）
    private static final long COOLDOWN_PERIOD = 30000; // 超过限制后的冷却时间（30秒）

    // 编译正则表达式模式（支持多个触发词，忽略大小写，单词边界匹配）
    private static final Pattern TRIGGER_PATTERN = Pattern.compile(
            "\\b(?i)(" +
                    Pattern.quote(TRIGGER_PREFIX) + "|" +
                    Pattern.quote(TRIGGER_PREFIX2) + "|" +
                    Pattern.quote(TRIGGER_PREFIX3) +
                    ")\\b"
    );

    private static final AtomicBoolean isProcessing = new AtomicBoolean(false);

    // 频率限制相关变量
    private static final AtomicInteger requestCount = new AtomicInteger(0);
    private static volatile long lastResetTime = System.currentTimeMillis();
    private static volatile long cooldownEndTime = 0;
    private static final ConcurrentHashMap<String, Long> userLastRequestTime = new ConcurrentHashMap<>();

    /**
     * 初始化监听器
     */
    public static void initialize() {
        LOGGER.info("初始化AI聊天监听器，支持触发词: {}, {}, {}",
                TRIGGER_PREFIX, TRIGGER_PREFIX2, TRIGGER_PREFIX3);
        LOGGER.info("频率限制: 每分钟{}次请求，冷却时间: {}秒",
                RATE_LIMIT_MAX_REQUESTS, COOLDOWN_PERIOD / 1000);

        // 监听游戏内消息
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                handleMessage(message.getString());
            }
        });

        // 监听聊天消息
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            handleMessage(message.getString());
        });

        // 启动频率限制计时器
        startRateLimitTimer();

        LOGGER.info("多触发词AI聊天监听器已就绪（带频率限制）");
    }

    /**
     * 启动频率限制计时器（每分钟重置计数器）
     */
    private static void startRateLimitTimer() {
        CompletableFuture.runAsync(() -> {
            while (true) {
                try {
                    Thread.sleep(60000); // 每分钟检查一次
                    resetRateLimit();
                } catch (InterruptedException e) {
                    LOGGER.warn("频率限制计时器被中断", e);
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    /**
     * 重置频率限制计数器
     */
    private static synchronized void resetRateLimit() {
        int oldCount = requestCount.getAndSet(0);
        lastResetTime = System.currentTimeMillis();

        if (oldCount > 0) {
            LOGGER.debug("频率限制计数器已重置，上一分钟请求数: {}", oldCount);
        }
    }

    /**
     * 检查频率限制
     */
    private static synchronized boolean checkRateLimit() {
        long currentTime = System.currentTimeMillis();

        // 检查冷却期
        if (currentTime < cooldownEndTime) {
            long remainingCooldown = (cooldownEndTime - currentTime) / 1000;
            LOGGER.warn("系统处于冷却期，剩余: {}秒", remainingCooldown);
            return false;
        }

        // 检查时间窗口是否过期
        if (currentTime - lastResetTime > RATE_LIMIT_TIME_WINDOW) {
            resetRateLimit();
        }

        // 检查请求次数
        int currentCount = requestCount.get();
        if (currentCount >= RATE_LIMIT_MAX_REQUESTS) {
            cooldownEndTime = currentTime + COOLDOWN_PERIOD;
            LOGGER.warn("触发频率限制，进入冷却期（{}秒）", COOLDOWN_PERIOD / 1000);
            return false;
        }

        // 增加计数
        requestCount.incrementAndGet();
        return true;
    }

    /**
     * 获取当前频率限制状态
     */
    public static String getRateLimitStatus() {
        long currentTime = System.currentTimeMillis();
        long timeSinceReset = currentTime - lastResetTime;
        long timeRemaining = RATE_LIMIT_TIME_WINDOW - timeSinceReset;

        if (currentTime < cooldownEndTime) {
            long cooldownRemaining = (cooldownEndTime - currentTime) / 1000;
            return String.format("冷却中: %d秒后恢复", cooldownRemaining);
        }

        int requestsUsed = requestCount.get();
        int requestsRemaining = RATE_LIMIT_MAX_REQUESTS - requestsUsed;

        return String.format("剩余请求: %d/%d (%.1f秒后重置)",
                requestsRemaining, RATE_LIMIT_MAX_REQUESTS, timeRemaining / 1000.0);
    }

    /**
     * 处理消息（支持多个触发词和频率限制）
     */
    private static void handleMessage(String message) {
        String plainText = message.replaceAll("§[0-9a-fk-or]", "");
        Matcher matcher = TRIGGER_PATTERN.matcher(plainText);

        if (matcher.find() && isProcessing.compareAndSet(false, true)) {
            try {
                // 检查频率限制
                if (!checkRateLimit()) {
                    sendRateLimitMessage();
                    return;
                }

                String matchedTrigger = matcher.group(1).toLowerCase();
                String userMessage = extractUserMessage(plainText, matchedTrigger);

                LOGGER.debug("检测到触发词: '{}', 原始消息: '{}'", matchedTrigger, plainText);
                LOGGER.info("当前频率状态: {}", getRateLimitStatus());

                if (!userMessage.isEmpty()) {
                    processRequest(userMessage, matchedTrigger);
                } else {
                    handleEmptyInput(matchedTrigger);
                }
            } finally {
                isProcessing.set(false);
            }
        }
    }

    /**
     * 发送频率限制提示消息
     */
    private static void sendRateLimitMessage() {
        String statusMessage = getRateLimitStatus();
        if (statusMessage.startsWith("冷却中")) {
            sendChatMessage("AI服务冷却中，" + statusMessage);
        } else {
            sendChatMessage("AI使用频率过高，请稍后再试。当前状态: " + statusMessage);
        }
    }

    /**
     * 提取用户消息
     */
    private static String extractUserMessage(String fullMessage, String trigger) {
        return fullMessage.replaceFirst("(?i)\\b" + Pattern.quote(trigger) + "\\b", "").trim();
    }

    /**
     * 处理空输入
     */
    private static void handleEmptyInput(String trigger) {
        // 空输入也消耗一次请求次数
        String[] responses = {
                "请告诉我你想聊什么~",
                "触发词后需要输入内容哦",
                "你想和我聊什么呢？",
                "我在听，请说...",
                "需要我帮忙吗？请告诉我具体内容"
        };

        String response = responses[(int)(Math.random() * responses.length)];
        sendChatMessage(response);

        LOGGER.debug("空输入触发，使用触发词: {}", trigger);
    }

    /**
     * 处理AI请求
     */
    private static void processRequest(String userMessage, String triggerUsed) {
        LOGGER.info("处理AI请求，触发词: '{}', 内容: '{}'", triggerUsed, userMessage);
        LOGGER.info("当前请求计数: {}/{}", requestCount.get(), RATE_LIMIT_MAX_REQUESTS);

        CompletableFuture.runAsync(() -> {
            try {
                String aiResponse = AIChatService.getAIResponse(userMessage);
                sendChatMessage(aiResponse);

                // 可根据不同触发词记录统计信息
                logTriggerUsage(triggerUsed, userMessage.length());
            } catch (Exception e) {
                LOGGER.error("AI请求处理失败，触发词: {}", triggerUsed, e);
                sendChatMessage("AI服务暂时不可用，请稍后再试");
            }
        });
    }

    /**
     * 发送聊天消息
     */
    private static void sendChatMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.execute(() -> {
                try {
                    ClientPlayerEntity player = client.player;
                    // 只发送一次消息，避免重复
                    player.networkHandler.sendChatCommand("c " + message);
                    client.player.networkHandler.sendChatMessage(message);
                    LOGGER.debug("已发送消息: {}", message);
                } catch (Exception e) {
                    LOGGER.error("发送消息失败", e);
                }
            });
        }
    }

    /**
     * 记录触发词使用情况
     */
    private static void logTriggerUsage(String trigger, int messageLength) {
        LOGGER.debug("触发词使用统计: {} (消息长度: {})", trigger, messageLength);
    }

    /**
     * 获取当前处理状态
     */
    public static boolean isProcessing() {
        return isProcessing.get();
    }

    /**
     * 获取频率限制状态
     */
    public static String getRateLimitInfo() {
        return getRateLimitStatus();
    }

    /**
     * 获取支持的触发词列表
     */
    public static String[] getSupportedTriggers() {
        return new String[]{TRIGGER_PREFIX, TRIGGER_PREFIX2, TRIGGER_PREFIX3};
    }

    /**
     * 手动重置频率限制（用于调试或特殊情况）
     */
    public static void resetRateLimitManually() {
        resetRateLimit();
        cooldownEndTime = 0;
        LOGGER.info("手动重置频率限制");
    }
}