package com.xiaofan.kookbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * 配置管理器
 * 从配置文件读取敏感信息，如果配置文件不存在则使用默认值
 */
public class ConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    
    private static final String CONFIG_FILE_NAME = "config.properties";
    private static Properties config;
    
    static {
        loadConfig();
    }
    
    /**
     * 加载配置文件
     * 优先从JAR包同目录加载，如果不存在则创建默认配置文件
     */
    private static void loadConfig() {
        config = new Properties();
        
        // 1. 尝试从JAR包同目录加载（用于生产环境）
        File jarDir = getJarDirectory();
        File configFile = new File(jarDir, CONFIG_FILE_NAME);
        
        if (configFile.exists() && configFile.isFile()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                config.load(fis);
                logger.info("已从JAR包同目录加载配置文件: {}", configFile.getAbsolutePath());
            } catch (IOException e) {
                logger.warn("读取JAR包同目录配置文件失败: {}", e.getMessage());
            }
        } else {
            // 2. 配置文件不存在，尝试创建默认配置文件
            logger.info("配置文件不存在，正在创建默认配置文件...");
            if (createDefaultConfigFile(configFile)) {
                // 创建成功后，重新加载
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    config.load(fis);
                    logger.info("已创建并加载默认配置文件: {}", configFile.getAbsolutePath());
                    logger.warn("⚠️ 请编辑配置文件并填入你的 KOOK Bot Token: {}", configFile.getAbsolutePath());
                } catch (IOException e) {
                    logger.warn("读取新创建的配置文件失败: {}", e.getMessage());
                }
            } else {
                // 创建失败，尝试从classpath加载（用于开发环境）
                logger.warn("无法创建配置文件，尝试从classpath加载...");
                try (InputStream is = ConfigManager.class.getClassLoader().getResourceAsStream("config.properties.example")) {
                    if (is != null) {
                        config.load(is);
                        logger.info("已从classpath加载示例配置文件");
                        logger.warn("⚠️ 请创建配置文件并填入你的 KOOK Bot Token");
                    } else {
                        logger.warn("配置文件不存在，将使用默认值（脱敏版本）");
                    }
                } catch (IOException e) {
                    logger.warn("读取classpath配置文件失败: {}", e.getMessage());
                }
            }
        }
    }
    
    /**
     * 创建默认配置文件
     * @param configFile 配置文件路径
     * @return 是否创建成功
     */
    private static boolean createDefaultConfigFile(File configFile) {
        try {
            // 确保目录存在
            File parentDir = configFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            // 尝试从classpath复制示例配置文件
            try (InputStream is = ConfigManager.class.getClassLoader().getResourceAsStream("config.properties.example")) {
                if (is != null) {
                    // 从示例文件复制
                    Files.copy(is, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    logger.info("已从示例文件创建配置文件: {}", configFile.getAbsolutePath());
                    return true;
                }
            }
            
            // 如果示例文件不存在，创建默认配置文件
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write("# KOOK Bot 配置文件\n");
                writer.write("# 请填入你的 KOOK Bot Token\n\n");
                writer.write("# KOOK Bot Token（从 KOOK 开发者平台获取）\n");
                writer.write("# 格式：Bot {token}，但这里只填 token 部分\n");
                writer.write("kook.bot.token=YOUR_BOT_TOKEN_HERE\n\n");
                writer.write("# KOOK API 地址（通常不需要修改）\n");
                writer.write("kook.api.url=https://www.kookapp.cn\n\n");
                writer.write("# KOOK WebSocket Gateway 地址\n");
                writer.write("# 如果为空，程序会自动获取\n");
                writer.write("kook.ws.url=\n\n");
                writer.write("# 数据库配置（如果需要复用数据库功能）\n");
                writer.write("db.url=jdbc:mysql://localhost:3306/qddata?useSSL=false&serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true\n");
                writer.write("db.user=root\n");
                writer.write("db.password=password\n\n");
                writer.write("# 日志级别\n");
                writer.write("logging.level=INFO\n");
                writer.flush();
                logger.info("已创建默认配置文件: {}", configFile.getAbsolutePath());
                return true;
            }
        } catch (IOException e) {
            logger.error("创建配置文件失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 获取JAR包所在目录
     */
    private static File getJarDirectory() {
        try {
            String path = ConfigManager.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
                    .getPath();
            File jarFile = new File(path);
            return jarFile.getParentFile();
        } catch (Exception e) {
            // 如果无法获取JAR目录，返回当前工作目录
            return new File(System.getProperty("user.dir"));
        }
    }
    
    /**
     * 获取配置值，如果不存在则返回默认值
     */
    private static String getProperty(String key, String defaultValue) {
        String value = config.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }
    
    // ========== KOOK Bot配置 ==========
    public static String getKookBotToken() {
        return getProperty("kook.bot.token", "YOUR_BOT_TOKEN_HERE");
    }
    
    public static String getKookApiUrl() {
        return getProperty("kook.api.url", "https://www.kookapp.cn");
    }
    
    public static String getKookWsUrl() {
        // KOOK WebSocket Gateway URL
        // 需要先调用 /api/v3/gateway/index 获取 WebSocket 地址
        return getProperty("kook.ws.url", "");
    }
    
    // ========== 数据库配置（如果需要复用） ==========
    public static String getDbUrl() {
        return getProperty("db.url", "jdbc:mysql://localhost:3306/qddata?useSSL=false&serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true");
    }
    
    public static String getDbUser() {
        return getProperty("db.user", "root");
    }
    
    public static String getDbPassword() {
        return getProperty("db.password", "password");
    }
    
    /**
     * 检查关键配置是否已设置（用于启动时验证）
     */
    public static boolean validateConfig() {
        boolean valid = true;
        
        if ("YOUR_BOT_TOKEN_HERE".equals(getKookBotToken())) {
            logger.warn("⚠️ KOOK Bot Token未配置，请设置 config.properties 中的 kook.bot.token");
            valid = false;
        }
        
        return valid;
    }
}

