package com.xiaofan.qqbot.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * 绑定会话管理器
 * 管理单个绑定会话，确保同时只有一个绑定会话在进行
 */
public class BindingSessionManager {
    private static final Logger logger = LoggerFactory.getLogger(BindingSessionManager.class);
    
    // 单例模式
    private static final BindingSessionManager instance = new BindingSessionManager();
    
    // 会话信息
    private volatile BindingSession currentSession = null;
    private final Object sessionLock = new Object();
    
    // 定时任务执行器
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> timeoutTask = null;
    
    // 验证码超时时间（2分钟）
    private static final long TIMEOUT_MINUTES = 2;
    
    // 超时回调函数
    private BiConsumer<Long, Long> timeoutCallback = null;
    
    private BindingSessionManager() {
    }
    
    /**
     * 设置超时回调
     * @param callback 回调函数，参数为(groupId, qqId)
     */
    public void setTimeoutCallback(BiConsumer<Long, Long> callback) {
        this.timeoutCallback = callback;
    }
    
    public static BindingSessionManager getInstance() {
        return instance;
    }
    
    /**
     * 绑定会话信息
     */
    public static class BindingSession {
        public long qqId;
        public long groupId;
        public String passCode;
        public long startTime;
        
        public BindingSession(long qqId, long groupId, String passCode) {
            this.qqId = qqId;
            this.groupId = groupId;
            this.passCode = passCode;
            this.startTime = System.currentTimeMillis();
        }
    }
    
    /**
     * 开始新的绑定会话
     * @param qqId QQ号
     * @param groupId 群号
     * @return 如果成功开始会话返回验证码，如果已有会话返回null
     */
    public String startSession(long qqId, long groupId) {
        synchronized (sessionLock) {
            if (currentSession != null) {
                logger.warn("已有绑定会话在进行，QQ号: {}, 当前会话QQ号: {}", qqId, currentSession.qqId);
                return null;
            }
            
            // 生成6位随机数字验证码
            Random random = new Random();
            String passCode = String.format("%06d", random.nextInt(1000000));
            
            currentSession = new BindingSession(qqId, groupId, passCode);
            
            // 设置2分钟超时任务
            final long finalQqId = qqId;
            final long finalGroupId = groupId;
            timeoutTask = scheduler.schedule(() -> {
                synchronized (sessionLock) {
                    if (currentSession != null && currentSession.qqId == finalQqId) {
                        logger.info("绑定会话超时，QQ号: {}", finalQqId);
                        if (timeoutCallback != null) {
                            timeoutCallback.accept(finalGroupId, finalQqId);
                        }
                        endSession();
                    }
                }
            }, TIMEOUT_MINUTES, TimeUnit.MINUTES);
            
            logger.info("绑定会话已开始，QQ号: {}, 群号: {}, 验证码: {}", qqId, groupId, passCode);
            return passCode;
        }
    }
    
    /**
     * 验证验证码并获取会话
     * @param passCode 验证码
     * @return 如果验证码匹配返回会话，否则返回null
     */
    public BindingSession verifyAndGetSession(String passCode) {
        synchronized (sessionLock) {
            if (currentSession == null) {
                logger.warn("验证码验证失败：没有活跃的绑定会话");
                return null;
            }
            
            if (currentSession.passCode.equals(passCode)) {
                logger.info("验证码验证成功，QQ号: {}", currentSession.qqId);
                return currentSession;
            } else {
                logger.warn("验证码验证失败，期望: {}, 实际: {}", currentSession.passCode, passCode);
                return null;
            }
        }
    }
    
    /**
     * 结束当前会话
     */
    public void endSession() {
        synchronized (sessionLock) {
            if (currentSession != null) {
                logger.info("绑定会话已结束，QQ号: {}", currentSession.qqId);
                currentSession = null;
            }
            
            if (timeoutTask != null && !timeoutTask.isDone()) {
                timeoutTask.cancel(false);
                timeoutTask = null;
            }
        }
    }
    
    /**
     * 检查是否有活跃的会话
     * @return 如果有活跃会话返回true
     */
    public boolean hasActiveSession() {
        synchronized (sessionLock) {
            return currentSession != null;
        }
    }
    
    /**
     * 获取当前会话的QQ号（用于检查是否有人在绑定）
     * @return 当前会话的QQ号，如果没有会话返回0
     */
    public long getCurrentSessionQqId() {
        synchronized (sessionLock) {
            return currentSession != null ? currentSession.qqId : 0;
        }
    }
    
    /**
     * 关闭管理器（清理资源）
     */
    public void shutdown() {
        synchronized (sessionLock) {
            endSession();
            scheduler.shutdown();
        }
    }
}
