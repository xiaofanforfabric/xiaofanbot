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
 * 群聊静默模式管理器
 * 管理处于静默模式的群列表，从silent_groups.txt文件加载
 */
public class SilentModeManager {
    private static final Logger logger = LoggerFactory.getLogger(SilentModeManager.class);
    
    private static final String SILENT_FILE_NAME = "silent_groups.txt";
    
    private final Set<Long> silentGroupIds = new HashSet<>();
    private final Path silentFilePath;
    private boolean initialized = false;
    
    public SilentModeManager() {
        // 获取JAR所在目录
        String jarPath = getJarDirectory();
        this.silentFilePath = Paths.get(jarPath, SILENT_FILE_NAME);
        initialize();
    }
    
    /**
     * 初始化静默模式管理器
     * 如果silent_groups.txt不存在则创建，如果存在则加载
     */
    private void initialize() {
        if (initialized) {
            return;
        }
        
        try {
            // 确保目录存在
            Path parentDir = silentFilePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            
            // 如果文件不存在，创建空文件
            if (!Files.exists(silentFilePath)) {
                Files.createFile(silentFilePath);
                logger.info("[静默模式] 已创建静默模式文件: {}", silentFilePath);
            } else {
                logger.info("[静默模式] 静默模式文件已存在: {}", silentFilePath);
            }
            
            // 加载静默模式群列表
            loadSilentGroups();
            
            initialized = true;
            logger.info("[静默模式] 静默模式管理器初始化完成，已加载 {} 个静默群", silentGroupIds.size());
            
        } catch (Exception e) {
            logger.error("[静默模式] 初始化失败", e);
        }
    }
    
    /**
     * 从文件加载静默模式群列表
     */
    private void loadSilentGroups() {
        silentGroupIds.clear();
        
        try {
            if (!Files.exists(silentFilePath)) {
                logger.warn("[静默模式] 静默模式文件不存在: {}", silentFilePath);
                return;
            }
            
            try (BufferedReader reader = Files.newBufferedReader(silentFilePath)) {
                String line;
                int lineNumber = 0;
                
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    line = line.trim();
                    
                    // 跳过空行和注释行
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    
                    // 解析群号
                    try {
                        long groupId = Long.parseLong(line);
                        if (groupId > 0) {
                            silentGroupIds.add(groupId);
                            logger.debug("[静默模式] 加载静默群号: {}", groupId);
                        } else {
                            logger.warn("[静默模式] 第{}行 无效的群号(必须大于0): {}", lineNumber, line);
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("[静默模式] 第{}行 无法解析群号: {}", lineNumber, line);
                    }
                }
            }
            
            logger.info("[静默模式] 成功加载 {} 个静默群", silentGroupIds.size());
            
        } catch (IOException e) {
            logger.error("[静默模式] 读取静默模式文件失败: {}", silentFilePath, e);
        }
    }
    
    /**
     * 检查群是否处于静默模式
     * @param groupId 群号
     * @return 如果处于静默模式返回true
     */
    public boolean isSilent(long groupId) {
        if (!initialized) {
            initialize();
        }
        return silentGroupIds.contains(groupId);
    }
    
    /**
     * 开启群聊静默模式
     * @param groupId 群号
     * @return 如果成功开启返回true，如果已在静默模式返回false
     */
    public boolean enableSilent(long groupId) {
        if (!initialized) {
            initialize();
        }
        
        if (groupId <= 0) {
            logger.warn("[静默模式] 无效的群号: {}", groupId);
            return false;
        }
        
        if (silentGroupIds.contains(groupId)) {
            logger.info("[静默模式] 群号 {} 已在静默模式中", groupId);
            return false;
        }
        
        silentGroupIds.add(groupId);
        logger.info("[静默模式] 开启群号 {} 的静默模式", groupId);
        
        // 持久化到文件
        saveSilentGroups();
        
        return true;
    }
    
    /**
     * 关闭群聊静默模式
     * @param groupId 群号
     * @return 如果成功关闭返回true，如果不在静默模式返回false
     */
    public boolean disableSilent(long groupId) {
        if (!initialized) {
            initialize();
        }
        
        if (groupId <= 0) {
            logger.warn("[静默模式] 无效的群号: {}", groupId);
            return false;
        }
        
        if (!silentGroupIds.contains(groupId)) {
            logger.info("[静默模式] 群号 {} 不在静默模式中", groupId);
            return false;
        }
        
        silentGroupIds.remove(groupId);
        logger.info("[静默模式] 关闭群号 {} 的静默模式", groupId);
        
        // 持久化到文件
        saveSilentGroups();
        
        return true;
    }
    
    /**
     * 保存静默模式群列表到文件
     */
    private void saveSilentGroups() {
        try {
            // 确保目录存在
            Path parentDir = silentFilePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            
            // 写入文件（覆盖模式，确保文件只包含当前静默状态的群号）
            try (BufferedWriter writer = Files.newBufferedWriter(
                    silentFilePath, 
                    StandardOpenOption.CREATE, 
                    StandardOpenOption.TRUNCATE_EXISTING, 
                    StandardOpenOption.WRITE)) {
                for (Long groupId : silentGroupIds) {
                    writer.write(String.valueOf(groupId));
                    writer.newLine();
                }
            }
            
            logger.info("[静默模式] 静默模式群列表已保存到文件: {}", silentFilePath);
        } catch (IOException e) {
            logger.error("[静默模式] 保存静默模式群列表到文件失败: {}", silentFilePath, e);
        }
    }
    
    /**
     * 获取静默模式文件路径
     */
    public Path getSilentFilePath() {
        return silentFilePath;
    }
    
    /**
     * 获取JAR文件所在目录
     */
    private String getJarDirectory() {
        try {
            // 从保护域获取代码源位置
            java.net.URL codeSourceUrl = SilentModeManager.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation();
            
            // 转换为URI再转换为Path，这样可以正确处理Windows路径
            java.nio.file.Path codeSourcePath = Paths.get(codeSourceUrl.toURI());
            
            // 如果是JAR文件，获取其所在目录
            if (codeSourcePath.toString().endsWith(".jar")) {
                java.nio.file.Path parentPath = codeSourcePath.getParent();
                if (parentPath != null) {
                    return parentPath.toString();
                }
            }
            
            // 如果是开发环境（class文件），使用当前工作目录
            return System.getProperty("user.dir");
            
        } catch (Exception e) {
            logger.warn("[静默模式] 无法获取JAR目录，使用当前工作目录", e);
            // 如果无法获取，使用当前工作目录
            return System.getProperty("user.dir");
        }
    }
}
