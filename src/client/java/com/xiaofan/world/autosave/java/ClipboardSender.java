package com.xiaofan.world.autosave.java;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Timer;
import java.util.TimerTask;

public class ClipboardSender {
    private static final long INTERVAL_MINUTES = 1;
    private static final long INTERVAL_MILLIS = INTERVAL_MINUTES * 60 * 1000;
    private static final long COOLDOWN_MS = 5000;

    private final KeyBinding sendClipboardKey;
    private Timer timer;
    private long lastSentTime = 0;
    private boolean enabled = true;

    public ClipboardSender() {
        this.sendClipboardKey = new KeyBinding(
                "key.clipboardsender.send",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_PAGE_DOWN,
                "category.clipboardsender.main"
        );
    }

    public void initialize() {
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (enabled && MinecraftClient.getInstance().player != null) {
                    sendClipboardContent();
                }
            }
        }, INTERVAL_MILLIS, INTERVAL_MILLIS);
    }

    public void onClientTick() {
        if (enabled && sendClipboardKey.wasPressed()) {
            sendClipboardContent();
        }
    }

    public KeyBinding getKeyBinding() {
        return sendClipboardKey;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private void sendClipboardContent() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.player.networkHandler == null) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSentTime < COOLDOWN_MS) {
            return;
        }

        try {
            long window = GLFW.glfwGetCurrentContext();
            String content = GLFW.glfwGetClipboardString(window);

            if (content != null && !content.trim().isEmpty()) {
                // 1.21.5 版本需要消息内容和签名两个参数
                client.player.networkHandler.sendChatMessage(content);
                lastSentTime = currentTime;

                // 可选：发送本地确认消息
                client.player.sendMessage(Text.literal("已发送剪贴板内容到聊天"), false);
            }
        } catch (Exception e) {
            client.player.sendMessage(Text.literal("剪贴板发送失败: " + e.getMessage()), false);
        }
    }

    public void shutdown() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
}