package com.xiaofan.qqbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/**
 * 黑名单管理器
 * 管理被禁止的QQ号列表，从ban.txt文件加载
 */
public class BanListManager {
    private static final Logger logger = LoggerFactory.getLogger(BanListManager.class);
    
    private static final String BAN_FILE_NAME = "ban.txt";
    private static final String BAN_MESSAGE = "you are banned server";
    
    private final Set<Long> bannedUserIds = new HashSet<>();
    private final Path banFilePath;
    private boolean initialized = false;
    
    public BanListManager() {
        // 获取JAR所在目录
        String jarPath = getJarDirectory();
        this.banFilePath = Paths.get(jarPath, BAN_FILE_NAME);
        initialize();
    }
    
    /**
     * 初始化黑名单管理器
     * 如果ban.txt不存在则创建，如果存在则加载
     */
    private void initialize() {
        if (initialized) {
            return;
        }
        
        try {
            // 确保目录存在
            Path parentDir = banFilePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            
            // 如果文件不存在，创建空文件
            if (!Files.exists(banFilePath)) {
                Files.createFile(banFilePath);
                logger.info("[黑名单] 已创建黑名单文件: {}", banFilePath);
            } else {
                logger.info("[黑名单] 黑名单文件已存在: {}", banFilePath);
            }
            
            // 加载黑名单
            loadBanList();
            
            initialized = true;
            logger.info("[黑名单] 黑名单管理器初始化完成，已加载 {} 个被禁止的QQ号", bannedUserIds.size());
            
        } catch (Exception e) {
            logger.error("[黑名单] 初始化失败", e);
        }
    }
    
    /**
     * 从文件加载黑名单
     */
    private void loadBanList() {
        bannedUserIds.clear();
        
        try {
            if (!Files.exists(banFilePath)) {
                logger.warn("[黑名单] 黑名单文件不存在: {}", banFilePath);
                return;
            }
            
            try (BufferedReader reader = Files.newBufferedReader(banFilePath)) {
                String line;
                int lineNumber = 0;
                
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    line = line.trim();
                    
                    // 跳过空行和注释行
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    
                    // 移除末尾的分号（如果存在）
                    if (line.endsWith(";")) {
                        line = line.substring(0, line.length() - 1).trim();
                    }
                    
                    // 解析QQ号
                    try {
                        long qqId = Long.parseLong(line);
                        if (qqId > 0) {
                            bannedUserIds.add(qqId);
                            logger.debug("[黑名单] 加载黑名单QQ号: {}", qqId);
                        } else {
                            logger.warn("[黑名单] 第{}行: 无效的QQ号 (必须大于0): {}", lineNumber, line);
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("[黑名单] 第{}行: 无法解析QQ号: {}", lineNumber, line);
                    }
                }
            }
            
            logger.info("[黑名单] 成功加载 {} 个黑名单QQ号", bannedUserIds.size());
            
        } catch (IOException e) {
            logger.error("[黑名单] 读取黑名单文件失败: {}", banFilePath, e);
        }
    }
    
    /**
     * 检查用户是否在黑名单中
     */
    public boolean isBanned(long userId) {
        if (!initialized) {
            initialize();
        }
        return bannedUserIds.contains(userId);
    }
    
    /**
     * 获取黑名单提示消息
     */
    public String getBanMessage() {
        return BAN_MESSAGE;
    }
    
    /**
     * 重新加载黑名单（用于运行时更新）
     */
    public void reload() {
        logger.info("[黑名单] 重新加载黑名单...");
        loadBanList();
        logger.info("[黑名单] 重新加载完成，当前黑名单数量: {}", bannedUserIds.size());
    }
    
    /**
     * 获取黑名单文件路径
     */
    public Path getBanFilePath() {
        return banFilePath;
    }
    
    /**
     * 获取JAR文件所在目录
     */
    private String getJarDirectory() {
        try {
            // 方法1: 从保护域获取代码源位置
            String codeSourcePath = BanListManager.class.getProtectionDomain()
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
            logger.warn("[黑名单] 无法获取JAR目录，使用当前工作目录", e);
            // 如果无法获取，使用当前工作目录
            return System.getProperty("user.dir");
        }
    }
}


