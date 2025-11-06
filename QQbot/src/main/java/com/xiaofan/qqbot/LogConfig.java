package com.xiaofan.qqbot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

/**
 * 日志配置工具类
 * 配置日志输出到文件
 */
public class LogConfig {
    private static final String LOG_FILE_NAME = "last.log";
    private static boolean configured = false;
    private static PrintStream originalOut;
    private static PrintStream originalErr;
    
    /**
     * 配置日志输出到文件
     * 必须在任何LoggerFactory.getLogger()调用之前调用
     */
    public static void configure() {
        if (configured) {
            return;
        }
        
        try {
            // 获取JAR所在目录
            String jarDirectory = getJarDirectory();
            File logFile = new File(jarDirectory, LOG_FILE_NAME);
            
            // 如果文件存在，先删除（覆盖模式）
            if (logFile.exists()) {
                logFile.delete();
            }
            
            // 创建日志文件输出流
            FileOutputStream fileOutputStream = new FileOutputStream(logFile, false); // false表示覆盖模式
            
            // 保存原始输出流
            originalOut = System.out;
            originalErr = System.err;
            
            // 创建同时输出到文件和控制台的PrintStream
            TeePrintStream teeOut = new TeePrintStream(originalOut, fileOutputStream, true);
            TeePrintStream teeErr = new TeePrintStream(originalErr, fileOutputStream, true);
            
            // 重定向System.out和System.err
            System.setOut(teeOut);
            System.setErr(teeErr);
            
            configured = true;
            
            // 输出配置信息
            System.out.println("日志配置完成，日志文件: " + logFile.getAbsolutePath());
            
        } catch (Exception e) {
            System.err.println("配置日志失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 同时输出到多个流的PrintStream
     */
    private static class TeePrintStream extends PrintStream {
        private final PrintStream[] streams;
        
        public TeePrintStream(PrintStream... streams) {
            super(streams[0]); // 使用第一个流作为主输出
            this.streams = streams;
        }
        
        public TeePrintStream(PrintStream stream1, FileOutputStream stream2, boolean autoFlush) {
            super(stream1, autoFlush);
            this.streams = new PrintStream[]{stream1, new PrintStream(stream2, autoFlush)};
        }
        
        @Override
        public void write(int b) {
            for (PrintStream stream : streams) {
                stream.write(b);
            }
        }
        
        @Override
        public void write(byte[] buf, int off, int len) {
            for (PrintStream stream : streams) {
                stream.write(buf, off, len);
            }
        }
        
        @Override
        public void flush() {
            for (PrintStream stream : streams) {
                stream.flush();
            }
        }
        
        @Override
        public void close() {
            // 不要关闭原始流，只关闭文件流
            for (int i = 1; i < streams.length; i++) {
                streams[i].close();
            }
        }
    }
    
    /**
     * 获取JAR文件所在目录
     */
    private static String getJarDirectory() {
        try {
            // 方法1: 从保护域获取代码源位置
            String codeSourcePath = LogConfig.class.getProtectionDomain()
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
            // 如果无法获取，使用当前工作目录
            return System.getProperty("user.dir");
        }
    }
}

