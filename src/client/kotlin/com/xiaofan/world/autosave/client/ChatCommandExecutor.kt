package com.xiaofan.world.autosave.client

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text

/**
 * 客户端聊天检查器，用于监听服务器聊天消息并执行命令
 */
object ChatCommandExecutor1 {
    private var client: MinecraftClient? = null
    private const val TARGET_PHRASE = "忠诚度低下"
    private const val COMMAND = "/u restore confirm"

    /**
     * 初始化客户端监听
     */
    fun initialize() {
        // 获取客户端实例
        ClientTickEvents.START_CLIENT_TICK.register(ClientTickEvents.StartTick { c ->
            client = c
        })

        // 监听接收到的聊天消息（来自服务器）
        ClientReceiveMessageEvents.ALLOW_GAME.register(ClientReceiveMessageEvents.AllowGame { message, overlay ->
            checkAndExecuteCommand(message.string, overlay)
            true // 允许消息正常显示
        })
    }

    /**
     * 检查聊天消息并在满足条件时执行命令
     */
    private fun checkAndExecuteCommand(message: String, overlay: Boolean) {
        if (message.contains(TARGET_PHRASE) && !overlay) {
            // 在客户端日志中记录
            println("检测到关键词 '$TARGET_PHRASE'，正在执行命令: $COMMAND")

            // 执行客户端命令
            executeClientCommand(COMMAND)
        }
    }

    /**
     * 执行客户端命令
     */
    private fun executeClientCommand(command: String) {
        client?.let {
            try {
                // 使用客户端网络处理器发送命令
                it.networkHandler?.sendChatCommand(command.removePrefix("/"))
            } catch (e: Exception) {
                println("执行命令时出错: ${e.message}")
            }
        }
    }
}