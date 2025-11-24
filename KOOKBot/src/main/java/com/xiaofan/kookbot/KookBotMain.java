package com.xiaofan.kookbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * KOOK机器人主程序入口
 * 
 * 使用 WebSocket 直接连接 KOOK Gateway
 */
public class KookBotMain {
    private static Logger logger;
    private static KookBot bot;
    
    public static void main(String[] args) {
        // 配置日志文件路径（与jar同目录）
        configureLogFile();
        
        // 初始化Logger
        logger = LoggerFactory.getLogger(KookBotMain.class);
        
        logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        logger.info("KOOK机器人服务启动...");
        
        // 验证配置
        ConfigManager.validateConfig();
        
        logger.info("KOOK API地址: {}", ConfigManager.getKookApiUrl());
        logger.info("KOOK WebSocket地址: {}", ConfigManager.getKookWsUrl().isEmpty() ? "将自动获取" : ConfigManager.getKookWsUrl());
        logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        // 初始化并启动机器人
        bot = new KookBot();
        bot.start();
        
        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("正在关闭服务...");
            if (bot != null) {
                bot.stop();
            }
            logger.info("服务已关闭");
        }));
        
        // 主线程保持运行
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            logger.error("主线程被中断", e);
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 配置日志文件路径（与jar同目录）
     * 如果日志文件存在则删除（覆盖模式）
     */
    private static void configureLogFile() {
        try {
            // 获取jar包所在目录
            File jarDir = getJarDirectory();
            File logFile = new File(jarDir, "kookbot.log");
            
            // 如果日志文件存在，删除它（覆盖模式）
            if (logFile.exists()) {
                boolean deleted = logFile.delete();
                if (deleted) {
                    System.out.println("已删除旧日志文件: " + logFile.getAbsolutePath());
                } else {
                    System.err.println("警告: 无法删除旧日志文件: " + logFile.getAbsolutePath());
                }
            }
            
            // 设置系统属性，logback会读取这个属性
            System.setProperty("LOG_DIR", jarDir.getAbsolutePath() + File.separator);
            
        } catch (Exception e) {
            System.err.println("配置日志文件路径失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 获取JAR包所在目录
     */
    private static File getJarDirectory() {
        try {
            // 获取当前类的保护域和代码源
            Path path = Paths.get(KookBotMain.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
            
            // 如果是JAR文件，返回父目录；如果是目录，返回自身
            if (Files.isRegularFile(path)) {
                return path.getParent().toFile();
            } else {
                return path.toFile();
            }
        } catch (Exception e) {
            // 如果获取失败，使用当前工作目录
            return new File(System.getProperty("user.dir"));
        }
    }
}

