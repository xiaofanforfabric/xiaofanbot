package com.xiaofan.qqbot.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

/**
 * 屏蔽列表管理器
 * 管理被屏蔽的游戏ID列表，从mute.txt文件加载
 */
public class MuteListManager {
    private static final Logger logger = LoggerFactory.getLogger(MuteListManager.class);
    
    private static final String MUTE_FILE_NAME = "mute.txt";
    
    private final Set<String> mutedGameIds = new HashSet<>();
    private final Path muteFilePath;
    private boolean initialized = false;
    
    public MuteListManager() {
        // 获取JAR所在目录
        String jarPath = getJarDirectory();
        this.muteFilePath = Paths.get(jarPath, MUTE_FILE_NAME);
        initialize();
    }
    
    /**
     * 初始化屏蔽列表管理器
     * 如果mute.txt不存在则创建，如果存在则加载
     */
    private void initialize() {
        if (initialized) {
            return;
        }
        
        try {
            // 确保目录存在
            Path parentDir = muteFilePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            
            // 如果文件不存在，创建空文件
            if (!Files.exists(muteFilePath)) {
                Files.createFile(muteFilePath);
                logger.info("[屏蔽列表] 已创建屏蔽列表文件: {}", muteFilePath);
            } else {
                logger.info("[屏蔽列表] 屏蔽列表文件已存在: {}", muteFilePath);
            }
            
            // 加载屏蔽列表
            loadMuteList();
            
            initialized = true;
            logger.info("[屏蔽列表] 屏蔽列表管理器初始化完成，已加载 {} 个被屏蔽的游戏ID", mutedGameIds.size());
            
        } catch (Exception e) {
            logger.error("[屏蔽列表] 初始化失败", e);
        }
    }
    
    /**
     * 从文件加载屏蔽列表
     */
    private void loadMuteList() {
        mutedGameIds.clear();
        
        try {
            if (!Files.exists(muteFilePath)) {
                logger.warn("[屏蔽列表] 屏蔽列表文件不存在: {}", muteFilePath);
                return;
            }
            
            try (BufferedReader reader = Files.newBufferedReader(muteFilePath)) {
                String line;
                
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    
                    // 跳过空行和注释行
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    
                    // 添加游戏ID（不区分大小写，统一转为小写存储）
                    if (!line.isEmpty()) {
                        mutedGameIds.add(line.toLowerCase());
                        logger.debug("[屏蔽列表] 加载屏蔽游戏ID: {}", line);
                    }
                }
            }
            
            logger.info("[屏蔽列表] 成功加载 {} 个屏蔽游戏ID", mutedGameIds.size());
            
        } catch (IOException e) {
            logger.error("[屏蔽列表] 读取屏蔽列表文件失败: {}", muteFilePath, e);
        }
    }
    
    /**
     * 添加游戏ID到屏蔽列表
     * @param gameId 游戏ID
     * @return 是否添加成功
     */
    public boolean addMutedGameId(String gameId) {
        if (gameId == null || gameId.trim().isEmpty()) {
            logger.warn("[屏蔽列表] 游戏ID为空，无法添加");
            return false;
        }
        
        String normalizedGameId = gameId.trim().toLowerCase();
        
        // 如果已经存在，直接返回成功
        if (mutedGameIds.contains(normalizedGameId)) {
            logger.info("[屏蔽列表] 游戏ID已在屏蔽列表中: {}", gameId);
            return true;
        }
        
        try {
            // 添加到内存集合
            mutedGameIds.add(normalizedGameId);
            
            // 追加到文件（使用追加模式）
            Files.write(muteFilePath, 
                (gameId.trim() + System.lineSeparator()).getBytes(),
                StandardOpenOption.CREATE, 
                StandardOpenOption.APPEND);
            
            logger.info("[屏蔽列表] 已添加屏蔽游戏ID: {}", gameId);
            return true;
            
        } catch (IOException e) {
            logger.error("[屏蔽列表] 写入屏蔽列表文件失败: {}", muteFilePath, e);
            // 从内存中移除（因为写入失败）
            mutedGameIds.remove(normalizedGameId);
            return false;
        }
    }
    
    /**
     * 检查游戏ID是否在屏蔽列表中
     * @param gameId 游戏ID
     * @return 如果被屏蔽返回true
     */
    public boolean isMuted(String gameId) {
        if (!initialized) {
            initialize();
        }
        if (gameId == null || gameId.trim().isEmpty()) {
            return false;
        }
        return mutedGameIds.contains(gameId.trim().toLowerCase());
    }
    
    /**
     * 检查消息中是否包含被屏蔽的游戏ID
     * 支持多种消息格式：
     * - "玩家名: 消息内容"
     * - "[前缀] 玩家名: 消息内容"
     * - 消息中包含玩家名
     * @param message 消息内容
     * @return 如果消息包含被屏蔽的游戏ID返回true
     */
    public boolean containsMutedGameId(String message) {
        if (!initialized) {
            initialize();
        }
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        
        String lowerMessage = message.toLowerCase();
        
        // 检查消息中是否包含任何被屏蔽的游戏ID
        // 优先检查消息是否以游戏ID开头（格式：游戏ID: 或游戏ID:消息）
        for (String mutedGameId : mutedGameIds) {
            // 检查消息是否以游戏ID开头（考虑冒号格式）
            if (lowerMessage.startsWith(mutedGameId + ":") || 
                lowerMessage.startsWith(mutedGameId + "：")) {
                logger.debug("[屏蔽列表] 消息以被屏蔽的游戏ID开头: {} (消息: {})", mutedGameId, message);
                return true;
            }
            
            // 检查消息中是否包含 "游戏ID:" 格式（可能前面有前缀）
            String pattern1 = mutedGameId + ":";
            String pattern2 = mutedGameId + "：";
            if (lowerMessage.contains(pattern1) || lowerMessage.contains(pattern2)) {
                logger.debug("[屏蔽列表] 消息包含被屏蔽的游戏ID: {} (消息: {})", mutedGameId, message);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 重新加载屏蔽列表（用于运行时更新）
     */
    public void reload() {
        logger.info("[屏蔽列表] 重新加载屏蔽列表...");
        loadMuteList();
        logger.info("[屏蔽列表] 重新加载完成，当前屏蔽列表数量: {}", mutedGameIds.size());
    }
    
    /**
     * 获取屏蔽列表文件路径
     */
    public Path getMuteFilePath() {
        return muteFilePath;
    }
    
    /**
     * 获取JAR文件所在目录
     */
    private String getJarDirectory() {
        try {
            // 方法1: 从保护域获取代码源位置
            String codeSourcePath = MuteListManager.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
                    .getPath();
            
            // 如果是JAR文件，获取其所在目录
            if (codeSourcePath.endsWith(".jar")) {
                return new File(codeSourcePath).getParent();
            }
            
            // 如果是开发环境（class文件），使用当前工作目录
            return System.getProperty("user.dir");
            
        } catch (Exception e) {
            logger.warn("[屏蔽列表] 无法获取JAR目录，使用当前工作目录", e);
            // 如果无法获取，使用当前工作目录
            return System.getProperty("user.dir");
        }
    }
}
