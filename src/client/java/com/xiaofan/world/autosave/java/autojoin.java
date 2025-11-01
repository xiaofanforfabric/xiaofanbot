package com.xiaofan.world.autosave.java;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.network.ServerAddress;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class autojoin {
    private static boolean shouldReconnect = false;
    private static long disconnectTime = 0;
    private static ServerInfo lastServer = null;
    private static int delayMs = 3000;

    public static void init() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (client.isIntegratedServerRunning()) return;

            shouldReconnect = true;
            disconnectTime = System.currentTimeMillis();
            lastServer = client.getCurrentServerEntry();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (shouldReconnect && System.currentTimeMillis() - disconnectTime >= delayMs) {
                attemptReconnect(client);
            }
        });
    }

    private static void attemptReconnect(MinecraftClient client) {
        if (lastServer != null) {
            ConnectScreen.connect(
                    new TitleScreen(),                      // MinecraftClient实例
                    client,           // 父屏幕
                                ServerAddress.parse(lastServer.address),  // 服务器地址
                                lastServer,                  // ServerInfo
                                false,                       // 是否快速连接
                                null                         // 连接回调（可为null）
                        );
        }
        shouldReconnect = false;
    }

    public static void setDelay(int delayMs) {
        autojoin.delayMs = delayMs;
    }
}