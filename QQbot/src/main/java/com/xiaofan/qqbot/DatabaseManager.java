package com.xiaofan.qqbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;

/**
 * 数据库连接管理器
 * 管理MySQL数据库连接，提供签到相关的数据库操作
 */
public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    
    // 数据库连接配置（从ConfigManager读取）
    private static final String DB_URL = ConfigManager.getDbUrl();
    private static final String DB_USER = ConfigManager.getDbUser();
    private static final String DB_PASSWORD = ConfigManager.getDbPassword();
    private static final String TABLE_NAME = "userdata";
    
    /**
     * 获取数据库连接
     */
    private Connection getConnection() throws SQLException {
        try {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            logger.debug("数据库连接成功");
            return conn;
        } catch (SQLException e) {
            String errorMsg = "数据库连接失败";
            if (e.getMessage().contains("连接被拒绝") || e.getMessage().contains("Communications link failure")) {
                errorMsg = "数据库连接失败：无法连接到MySQL服务器，请检查：\n" +
                          "1. MySQL服务是否已启动\n" +
                          "2. 数据库地址和端口是否正确（当前：" + DB_URL + "）\n" +
                          "3. 数据库用户名和密码是否正确";
            }
            logger.error(errorMsg, e);
            throw new SQLException(errorMsg, e);
        }
    }
    
    /**
     * 检查用户是否存在
     * @param qqId QQ号
     * @return 如果存在返回true，否则返回false
     */
    public boolean userExists(long qqId) {
        String sql = "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE qq_id = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, qqId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.error("查询用户是否存在时发生错误，QQ号: {}", qqId, e);
        }
        
        return false;
    }
    
    /**
     * 获取用户签到信息
     * @param qqId QQ号
     * @return 用户签到信息，如果不存在返回null
     */
    public UserCheckInInfo getUserInfo(long qqId) {
        String sql = "SELECT qq_id, qd, qd_last_time, reg_time FROM " + TABLE_NAME + " WHERE qq_id = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, qqId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    UserCheckInInfo info = new UserCheckInInfo();
                    info.qqId = rs.getLong("qq_id");
                    info.qd = rs.getInt("qd");
                    
                    Timestamp qdLastTime = rs.getTimestamp("qd_last_time");
                    if (qdLastTime != null) {
                        info.qdLastTime = qdLastTime.toLocalDateTime();
                    }
                    
                    Timestamp regTime = rs.getTimestamp("reg_time");
                    if (regTime != null) {
                        info.regTime = regTime.toLocalDateTime();
                    }
                    
                    return info;
                }
            }
        } catch (SQLException e) {
            logger.error("获取用户信息时发生错误，QQ号: {}", qqId, e);
        }
        
        return null;
    }
    
    /**
     * 注册新用户
     * @param qqId QQ号
     * @return 注册成功返回true，否则返回false
     */
    public boolean registerUser(long qqId) {
        LocalDateTime now = LocalDateTime.now();
        String sql = "INSERT INTO " + TABLE_NAME + " (qq_id, username, qd, qd_last_time, reg_time) VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, qqId);
            stmt.setString(2, String.valueOf(qqId)); // 使用QQ号作为默认用户名
            stmt.setInt(3, 1); // 初始积分为1
            stmt.setTimestamp(4, Timestamp.valueOf(now));
            stmt.setTimestamp(5, Timestamp.valueOf(now));
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                logger.info("用户注册成功，QQ号: {}, 初始积分: 1", qqId);
                return true;
            } else {
                logger.warn("用户注册失败，QQ号: {}", qqId);
                return false;
            }
        } catch (SQLException e) {
            logger.error("注册用户时发生错误，QQ号: {}", qqId, e);
            return false;
        }
    }
    
    /**
     * 更新签到信息
     * @param qqId QQ号
     * @return 更新成功返回true，否则返回false
     */
    public boolean updateCheckIn(long qqId) {
        LocalDateTime now = LocalDateTime.now();
        String sql = "UPDATE " + TABLE_NAME + " SET qd = qd + 1, qd_last_time = ? WHERE qq_id = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setTimestamp(1, Timestamp.valueOf(now));
            stmt.setLong(2, qqId);
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                logger.info("签到更新成功，QQ号: {}", qqId);
                return true;
            } else {
                logger.warn("签到更新失败，QQ号: {}", qqId);
                return false;
            }
        } catch (SQLException e) {
            logger.error("更新签到时发生错误，QQ号: {}", qqId, e);
            return false;
        }
    }
    
    /**
     * 插入投稿数据到tipdata表
     * @param tip 投稿内容
     * @param regUser 投稿用户的QQ号
     * @return 插入成功返回true，否则返回false
     */
    public boolean insertTip(String tip, String regUser) {
        LocalDateTime now = LocalDateTime.now();
        String sql = "INSERT INTO tipdata (tip, reg_user, reg_time) VALUES (?, ?, ?)";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, tip);
            stmt.setString(2, regUser);
            stmt.setTimestamp(3, Timestamp.valueOf(now));
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                logger.info("投稿插入成功，用户: {}", regUser);
                return true;
            } else {
                logger.warn("投稿插入失败，用户: {}", regUser);
                return false;
            }
        } catch (SQLException e) {
            logger.error("插入投稿时发生错误，用户: {}", regUser, e);
            return false;
        }
    }
    
    /**
     * 随机获取一条tipdata记录
     * @return TipInfo对象，如果没有数据返回null
     */
    public TipInfo getRandomTip() {
        // 使用ORDER BY RAND() LIMIT 1来随机获取一条记录
        String sql = "SELECT id, tip, reg_user, reg_time FROM tipdata ORDER BY RAND() LIMIT 1";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                TipInfo tipInfo = new TipInfo();
                tipInfo.id = rs.getInt("id");
                tipInfo.tip = rs.getString("tip");
                tipInfo.regUser = rs.getString("reg_user");
                
                Timestamp regTime = rs.getTimestamp("reg_time");
                if (regTime != null) {
                    tipInfo.regTime = regTime.toLocalDateTime();
                }
                
                logger.debug("随机获取tip成功，ID: {}", tipInfo.id);
                return tipInfo;
            }
        } catch (SQLException e) {
            logger.error("随机获取tip时发生错误", e);
        }
        
        return null;
    }
    
    /**
     * 用户签到信息数据类
     */
    public static class UserCheckInInfo {
        public long qqId;
        public int qd;
        public LocalDateTime qdLastTime;
        public LocalDateTime regTime;
    }
    
    /**
     * Tip数据类
     */
    public static class TipInfo {
        public int id;
        public String tip;
        public String regUser;
        public LocalDateTime regTime;
    }
}

