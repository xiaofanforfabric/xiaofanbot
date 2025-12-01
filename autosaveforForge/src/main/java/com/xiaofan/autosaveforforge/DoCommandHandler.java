package com.xiaofan.autosaveforforge;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * /do 客户端命令处理器
 * 通过拦截客户端发送的聊天消息来处理 /do list 和 /do <宏文件名> 命令
 * 这是纯客户端命令，不会发送到服务器
 */
public class DoCommandHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean initialized = false;
    
    /**
     * 初始化客户端命令处理器并注册事件监听
     */
    public static void initialize() {
        if (initialized) {
            return;
        }
        // 手动注册事件监听器
        MinecraftForge.EVENT_BUS.register(DoCommandHandler.class);
        initialized = true;
        LOGGER.info("[Do命令] 客户端命令处理器已初始化并注册事件监听");
    }
    
    /**
     * 拦截客户端发送的聊天消息，处理 /do 命令
     * 这是客户端命令，不会发送到服务器
     */
    @SubscribeEvent
    public static void onClientChat(ClientChatEvent event) {
        String message = event.getMessage();
        if (message == null || !message.trim().startsWith("/do")) {
            return;
        }
        
        LOGGER.info("[Do命令] 检测到 /do 命令: {}", message);
        
        // 取消发送到服务器（这是客户端命令）
        event.setCanceled(true);
        
        // 处理命令
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            LOGGER.warn("[Do命令] 玩家未初始化，无法执行命令");
            return;
        }
        
        String command = message.trim();
        String[] parts = command.split("\\s+", 3);
        
        if (parts.length == 1 || (parts.length == 2 && parts[1].equals("list"))) {
            // /do 或 /do list
            LOGGER.info("[Do命令] 执行 list 命令");
            listMacros(mc);
        } else if (parts.length >= 2) {
            // /do <宏文件名>
            String macroName = parts[1];
            LOGGER.info("[Do命令] 执行宏: {}", macroName);
            executeMacro(mc, macroName);
        }
    }
    
    /**
     * 列出所有宏文件
     */
    private static void listMacros(Minecraft mc) {
        try {
            BaritoneTaskManager manager = BaritoneTaskManager.getInstance();
            if (manager.getMacroFolder() == null) {
                manager.initialize();
            }
            
            File macroFolder = manager.getMacroFolder();
            if (macroFolder == null || !macroFolder.exists()) {
                sendMessage(mc, "§c宏文件夹不存在");
                return;
            }
            
            File[] files = macroFolder.listFiles((dir, name) -> name.endsWith(".txt"));
            if (files == null || files.length == 0) {
                sendMessage(mc, "§e没有找到宏文件");
                return;
            }
            
            List<String> macroNames = new ArrayList<>();
            for (File file : files) {
                macroNames.add(file.getName());
            }
            
            // 发送消息
            sendMessage(mc, "§a有 " + macroNames.size() + " 个宏文件：");
            
            for (int i = 0; i < macroNames.size(); i++) {
                sendMessage(mc, "§7" + (i + 1) + "." + macroNames.get(i));
            }
            
        } catch (Exception e) {
            LOGGER.error("[Do命令] 列出宏文件时出错", e);
            sendMessage(mc, "§c列出宏文件时出错: " + e.getMessage());
        }
    }
    
    /**
     * 执行宏
     */
    private static void executeMacro(Minecraft mc, String macroName) {
        try {
            // 如果用户输入了 .txt，去掉它
            if (macroName.endsWith(".txt")) {
                macroName = macroName.substring(0, macroName.length() - 4);
            }
            
            // 加载并执行宏
            BaritoneTaskManager manager = BaritoneTaskManager.getInstance();
            
            // 确保宏文件夹已初始化
            if (manager.getMacroFolder() == null) {
                manager.initialize();
            }
            
            // 加载宏文件
            File macroFile = new File(manager.getMacroFolder(), macroName + ".txt");
            if (!macroFile.exists()) {
                sendMessage(mc, "§c宏文件不存在: " + macroName + ".txt");
                return;
            }
            
            // 如果宏已经在运行，先停止
            if (manager.isMacroRunning(macroName)) {
                manager.stopMacro(macroName);
                sendMessage(mc, "§e已停止正在运行的宏: " + macroName);
            }
            
            // 加载宏
            try {
                Macro macro = MacroParser.parse(macroFile);
                manager.loadMacro(macroName, macro);
                
                // 启动宏
                manager.startMacro(macroName);
                sendMessage(mc, "§a已启动宏: " + macroName);
            } catch (NotFanMacroFound e) {
                LOGGER.error("[Do命令] 宏不存在: " + macroName);
                sendMessage(mc, "§c宏不存在: " + macroName);
            } catch (Exception e) {
                LOGGER.error("[Do命令] 加载宏文件失败: " + macroName, e);
                sendMessage(mc, "§c加载宏文件失败: " + e.getMessage());
            }
            
        } catch (Exception e) {
            LOGGER.error("[Do命令] 执行宏时出错", e);
            sendMessage(mc, "§c执行宏时出错: " + e.getMessage());
        }
    }
    
    /**
     * 发送消息到聊天
     */
    private static void sendMessage(Minecraft mc, String message) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(message));
        }
    }
}

