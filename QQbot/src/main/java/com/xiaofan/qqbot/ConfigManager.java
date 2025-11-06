package com.xiaofan.qqbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 配置管理器
 * 从配置文件读取敏感信息，如果配置文件不存在则使用默认值（用于GitHub公开版本）
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
     * 优先从JAR包同目录加载，如果不存在则从classpath加载
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
            // 2. 尝试从classpath加载（用于开发环境）
            try (InputStream is = ConfigManager.class.getClassLoader().getResourceAsStream(CONFIG_FILE_NAME)) {
                if (is != null) {
                    config.load(is);
                    logger.info("已从classpath加载配置文件");
                } else {
                    logger.warn("配置文件不存在，将使用默认值（脱敏版本）");
                }
            } catch (IOException e) {
                logger.warn("读取classpath配置文件失败: {}", e.getMessage());
            }
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
    
    // ========== NapCat配置 ==========
    public static String getNapCatApiUrl() {
        return getProperty("napcat.api.url", "http://127.0.0.1:3000");
    }
    
    public static String getNapCatWsUrl() {
        return getProperty("napcat.ws.url", "ws://127.0.0.1:3001");
    }
    
    public static String getNapCatToken() {
        return getProperty("napcat.token", "YOUR_TOKEN_HERE");
    }
    
    // ========== 数据库配置 ==========
    public static String getDbUrl() {
        return getProperty("db.url", "jdbc:mysql://localhost:3306/qddata?useSSL=false&serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true");
    }
    
    public static String getDbUser() {
        return getProperty("db.user", "root");
    }
    
    public static String getDbPassword() {
        return getProperty("db.password", "password");
    }
    
    // ========== Cloudflare AI配置 ==========
    public static String getCloudflareAccountId() {
        return getProperty("cloudflare.account.id", "YOUR_ACCOUNT_ID_HERE");
    }
    
    public static String getCloudflareApiKey() {
        // 优先从环境变量读取，其次从配置文件读取
        String envKey = System.getenv("AI_API_KEY");
        if (envKey != null && !envKey.trim().isEmpty()) {
            return envKey.trim();
        }
        return getProperty("cloudflare.api.key", "");
    }
    
    // ========== 其他配置 ==========
    public static String getTriggerMessage() {
        return getProperty("bot.trigger.message", "oi");
    }
    
    public static String getReplyMessage() {
        return getProperty("bot.reply.message", "io");
    }
    
    /**
     * 检查关键配置是否已设置（用于启动时验证）
     */
    public static boolean validateConfig() {
        boolean valid = true;
        
        if ("YOUR_TOKEN_HERE".equals(getNapCatToken())) {
            logger.warn("⚠️ NapCat Token未配置，请设置 config.properties 中的 napcat.token");
            valid = false;
        }
        
        if ("YOUR_ACCOUNT_ID_HERE".equals(getCloudflareAccountId())) {
            logger.warn("⚠️ Cloudflare Account ID未配置，请设置 config.properties 中的 cloudflare.account.id");
            // Account ID不是必须的，如果AI功能不使用可以不配置
        }
        
        if (getCloudflareApiKey().isEmpty()) {
            logger.warn("⚠️ Cloudflare API Key未配置（环境变量AI_API_KEY或config.properties中的cloudflare.api.key）");
            // API Key不是必须的，如果AI功能不使用可以不配置
        }
        
        return valid;
    }
}

