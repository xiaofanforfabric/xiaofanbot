package com.xiaofan.world.autosave.java;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;

public class ChatListener {
    public static void register() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String text = message.toString();
            webserver.addChatMessage(sender.getName().getString() + ": " + text);
        });

        ServerMessageEvents.GAME_MESSAGE.register((server, message, overlay) -> {
            if (!overlay) {
                webserver.addChatMessage("[系统] " + message.getString());
            }
        });
    }
}