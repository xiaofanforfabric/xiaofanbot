package com.xiaofan.autosaveforforge;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Baritone 自动任务管理器
 * 管理宏文件的加载、解析和执行
 */
public class BaritoneTaskManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MACRO_FOLDER_NAME = "do";
    private static BaritoneTaskManager instance;
    
    private final Map<String, Macro> macros = new ConcurrentHashMap<>();
    private final Map<String, MacroExecutor> runningExecutors = new ConcurrentHashMap<>();
    private final Map<String, String> macroCurrentCommands = new ConcurrentHashMap<>(); // 跟踪每个宏当前执行的命令类型
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private File macroFolder;
    private WatchService watchService;
    private Thread watchThread;
    private boolean isInitialized = false;
    
    // 定义冲突命令组（同一组内的命令不能同时执行）
    private static final Map<String, Set<String>> CONFLICT_GROUPS = new HashMap<>();
    static {
        // 路径查找组：这些命令会互相冲突
        Set<String> pathfindingGroup = new HashSet<>();
        pathfindingGroup.add("goto");
        pathfindingGroup.add("mine");
        pathfindingGroup.add("explore");
        pathfindingGroup.add("farm");
        pathfindingGroup.add("follow");
        CONFLICT_GROUPS.put("pathfinding", pathfindingGroup);
    }
    
    private BaritoneTaskManager() {
    }
    
    public static BaritoneTaskManager getInstance() {
        if (instance == null) {
            instance = new BaritoneTaskManager();
        }
        return instance;
    }
    
    /**
     * 初始化任务管理器（仅初始化文件夹，不加载宏）
     */
    public void initialize() {
        if (isInitialized) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            LOGGER.warn("[Baritone任务] Minecraft 客户端未初始化，延迟初始化");
            return;
        }
        
        // 获取配置文件夹
        File configDir = new File(mc.gameDirectory, "config");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        
        // 创建 do 文件夹
        macroFolder = new File(configDir, MACRO_FOLDER_NAME);
        if (!macroFolder.exists()) {
            macroFolder.mkdirs();
            LOGGER.info("[Baritone任务] 已创建宏文件夹: {}", macroFolder.getAbsolutePath());
        }
        
        // 启动文件监听
        startFileWatcher();
        
        // 注册事件监听
        MinecraftForge.EVENT_BUS.register(this);
        
        isInitialized = true;
        LOGGER.info("[Baritone任务] 任务管理器已初始化");
    }
    
    /**
     * 获取宏文件夹
     */
    public File getMacroFolder() {
        if (!isInitialized) {
            initialize();
        }
        return macroFolder;
    }
    
    /**
     * 手动加载宏
     */
    public void loadMacro(String macroName, Macro macro) {
        macros.put(macroName, macro);
        LOGGER.info("[Baritone任务] 已加载宏: {}", macroName);
    }
    
    /**
     * 加载所有宏文件
     */
    private void loadAllMacros() {
        if (macroFolder == null || !macroFolder.exists()) {
            return;
        }
        
        File[] files = macroFolder.listFiles((dir, name) -> name.endsWith(".txt"));
        if (files == null) {
            return;
        }
        
        for (File file : files) {
            try {
                String macroName = file.getName().replace(".txt", "");
                Macro macro = MacroParser.parse(file);
                macros.put(macroName, macro);
                LOGGER.info("[Baritone任务] 已加载宏: {}", macroName);
            } catch (Exception e) {
                LOGGER.error("[Baritone任务] 加载宏文件失败: {}", file.getName(), e);
            }
        }
    }
    
    /**
     * 启动文件监听器
     */
    private void startFileWatcher() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            Path path = macroFolder.toPath();
            path.register(watchService, 
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
            
            watchThread = new Thread(() -> {
                try {
                    while (isInitialized) {
                        WatchKey key = watchService.take();
                        for (WatchEvent<?> event : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = event.kind();
                            if (kind == StandardWatchEventKinds.OVERFLOW) {
                                continue;
                            }
                            
                            @SuppressWarnings("unchecked")
                            WatchEvent<Path> ev = (WatchEvent<Path>) event;
                            Path fileName = ev.context();
                            
                            if (fileName.toString().endsWith(".txt")) {
                                String macroName = fileName.toString().replace(".txt", "");
                                
                                if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                                    macros.remove(macroName);
                                    stopMacro(macroName);
                                    LOGGER.info("[Baritone任务] 已删除宏: {}", macroName);
                                } else {
                                    // 重新加载宏
                                    File file = new File(macroFolder, fileName.toString());
                                    if (file.exists()) {
                                        try {
                                            Macro macro = MacroParser.parse(file);
                                            macros.put(macroName, macro);
                                            LOGGER.info("[Baritone任务] 已重新加载宏: {}", macroName);
                                        } catch (Exception e) {
                                            LOGGER.error("[Baritone任务] 重新加载宏失败: {}", macroName, e);
                                        }
                                    }
                                }
                            }
                        }
                        key.reset();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    LOGGER.error("[Baritone任务] 文件监听器错误", e);
                }
            }, "BaritoneMacroWatcher");
            watchThread.setDaemon(true);
            watchThread.start();
            
        } catch (IOException e) {
            LOGGER.error("[Baritone任务] 启动文件监听器失败", e);
        }
    }
    
    /**
     * 启动宏
     * @param macroName 宏名称
     * @throws NotFanMacroFound 如果宏不存在
     */
    public void startMacro(String macroName) throws NotFanMacroFound {
        // 如果宏未加载，先尝试加载所有宏
        if (!macros.containsKey(macroName)) {
            loadAllMacros();
        }
        
        if (!macros.containsKey(macroName)) {
            LOGGER.warn("[Baritone任务] 宏不存在: {}", macroName);
            throw new NotFanMacroFound(macroName);
        }
        
        // 如果已经在运行，先停止（允许多个宏同时运行，但会检测冲突）
        // 注意：不再阻止多个宏同时运行，冲突会在执行命令时检测
        
        Macro macro = macros.get(macroName);
        MacroExecutor executor = new MacroExecutor(macroName, macro);
        runningExecutors.put(macroName, executor);
        
        executorService.submit(executor);
        LOGGER.info("[Baritone任务] 已启动宏: {}", macroName);
    }
    
    /**
     * 停止宏
     */
    public void stopMacro(String macroName) {
        MacroExecutor executor = runningExecutors.remove(macroName);
        if (executor != null) {
            executor.stop();
            LOGGER.info("[Baritone任务] 已停止宏: {}", macroName);
        }
    }
    
    /**
     * 从运行列表中移除宏（由 MacroExecutor 在完成时调用）
     * 这个方法用于确保宏执行完成后状态能正确同步
     */
    public void removeRunningMacro(String macroName) {
        MacroExecutor executor = runningExecutors.remove(macroName);
        macroCurrentCommands.remove(macroName);
        if (executor != null) {
            LOGGER.info("[Baritone任务] 宏执行完成，已从运行列表移除: {}", macroName);
        }
    }
    
    /**
     * 检查命令冲突并处理
     * @param macroName 当前执行的宏名
     * @param command 要执行的命令
     * @return true 如果允许执行，false 如果冲突且当前宏被停止
     */
    public boolean checkAndHandleConflict(String macroName, String command) {
        try {
            // 提取命令类型（去掉 # 和参数）
            String commandType = extractCommandType(command);
            if (commandType == null) {
                return true; // 无法识别的命令，允许执行
            }
            
            // 检查是否在冲突组中
            Set<String> conflictGroup = null;
            for (Set<String> group : CONFLICT_GROUPS.values()) {
                if (group.contains(commandType)) {
                    conflictGroup = group;
                    break;
                }
            }
            
            if (conflictGroup == null) {
                // 不在冲突组中，允许执行
                macroCurrentCommands.put(macroName, commandType);
                return true;
            }
            
            // 检查是否有其他宏正在执行冲突的命令
            for (Map.Entry<String, String> entry : macroCurrentCommands.entrySet()) {
                String otherMacro = entry.getKey();
                String otherCommand = entry.getValue();
                
                // 跳过自己
                if (otherMacro.equals(macroName)) {
                    continue;
                }
                
                // 检查是否在同一冲突组
                if (conflictGroup.contains(otherCommand)) {
                    // 发现冲突！停止后执行的宏（当前宏）
                    LOGGER.warn("[Baritone任务] 检测到命令冲突: 宏 {} 正在执行 {}，与宏 {} 的 {} 冲突", 
                        otherMacro, otherCommand, macroName, commandType);
                    
                    logToChatAndLogger(String.format("[宏冲突] 宏 %s 正在执行 %s，与宏 %s 的 %s 冲突，停止宏 %s", 
                        otherMacro, otherCommand, macroName, commandType, macroName));
                    
                    // 停止当前宏（后执行的）
                    stopMacro(macroName);
                    return false;
                }
            }
            
            // 没有冲突，记录当前命令
            macroCurrentCommands.put(macroName, commandType);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("[Baritone任务] 检查命令冲突时出错", e);
            // 出错时允许执行，避免阻塞
            return true;
        }
    }
    
    /**
     * 清除宏的命令记录（命令执行完成时调用）
     */
    public void clearMacroCommand(String macroName) {
        macroCurrentCommands.remove(macroName);
    }
    
    /**
     * 提取命令类型（去掉 # 和参数）
     */
    private String extractCommandType(String command) {
        if (command == null || command.isEmpty()) {
            return null;
        }
        
        String trimmed = command.trim();
        if (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1);
        }
        
        // 提取第一个单词（命令名）
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex > 0) {
            return trimmed.substring(0, spaceIndex).toLowerCase();
        }
        return trimmed.toLowerCase();
    }
    
    /**
     * 记录到日志和聊天框
     */
    private void logToChatAndLogger(String message) {
        LOGGER.info(message);
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            mc.execute(() -> {
                if (mc.player != null) {
                    mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7[宏冲突] §r" + message));
                }
            });
        }
    }
    
    /**
     * 停止所有宏
     */
    public void stopAllMacros() {
        for (String macroName : new ArrayList<>(runningExecutors.keySet())) {
            stopMacro(macroName);
        }
    }
    
    /**
     * 获取所有宏名称
     */
    public Set<String> getMacroNames() {
        return new HashSet<>(macros.keySet());
    }
    
    /**
     * 检查宏是否在运行
     */
    public boolean isMacroRunning(String macroName) {
        return runningExecutors.containsKey(macroName);
    }
    
    /**
     * 游戏刻事件，用于检查时间条件
     */
    @Mod.EventBusSubscriber(modid = Autosaveforforge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class GameTickHandler {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                return;
            }
            
            // 更新所有运行中的执行器
            BaritoneTaskManager manager = getInstance();
            for (MacroExecutor executor : new ArrayList<>(manager.runningExecutors.values())) {
                executor.onTick();
            }
        }
    }
    
    /**
     * 关闭任务管理器
     */
    public void shutdown() {
        isInitialized = false;
        stopAllMacros();
        
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                LOGGER.error("[Baritone任务] 关闭文件监听器失败", e);
            }
        }
        
        if (watchThread != null && watchThread.isAlive()) {
            watchThread.interrupt();
        }
        
        executorService.shutdown();
        LOGGER.info("[Baritone任务] 任务管理器已关闭");
    }
    
    /**
     * 获取当前游戏时间（刻）
     * 返回服务器世界时间，不是客户端本地时间
     */
    public static long getCurrentTime() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 0;
        }
        
        // getDayTime() 返回的是服务器同步的世界时间
        // 在多人游戏中，这是服务器的时间，不是客户端本地时间
        long worldTime = mc.level.getDayTime();
        
        // 返回一天内的时间（0-23999刻）
        long dayTime = worldTime % 24000;
        
        LOGGER.debug("[时间检查] 世界时间={}, 一天内时间={}", worldTime, dayTime);
        
        return dayTime;
    }
    
    /**
     * 获取玩家当前位置
     */
    public static BlockPos getPlayerPosition() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        return mc.player.blockPosition();
    }
}

