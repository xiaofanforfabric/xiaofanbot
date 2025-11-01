package com.xiaofan.world.autosave.client

import com.xiaofan.world.autosave.java.*
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import org.slf4j.LoggerFactory

class AutosaveClient : ClientModInitializer {
    private val logger = LoggerFactory.getLogger("AutosaveClient")
    private lateinit var clipboardSender: ClipboardSender
    private lateinit var aiChatService: AIChatService

    private var isProcessing = false

    override fun onInitializeClient() {
        logger.info("开始初始化邦国崛起AI聊天模组...")

        try {
            // 初始化AI聊天监听器
            AIChatListener.initialize()

            logger.info("邦国崛起AI聊天模组初始化成功！")
            logger.info("使用方式: 在聊天中输入 'xiaofanchat 你的消息' 与AI对话")

        } catch (e: Exception) {
            logger.error("模组初始化失败: ${e.message}", e)
        }


        println("[AutosaveClient] 模组加载成功！使用 'xiaofanchat 消息' 与AI对话")
        // 初始化服务
        aiChatService = AIChatService()
        clipboardSender = ClipboardSender().apply { initialize() }


        KeyBindingHelper.registerKeyBinding(clipboardSender.keyBinding)
        ClientTickEvents.END_CLIENT_TICK.register { _ -> clipboardSender.onClientTick() }

        // 初始化其他模块
        ChatCommandExecutor.initialize()
        //auto.initialize()
        webserver.initialize()
        ChatListener.register()
        ScreenLoggerUtil.init()

        println("[AutosaveClient] 模组加载成功！")
    }

}