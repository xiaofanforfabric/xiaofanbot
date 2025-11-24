package com.xiaofan.qqbot.handler;

import com.xiaofan.qqbot.game.MinesweeperGame;
import com.xiaofan.qqbot.game.MinesweeperRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 扫雷游戏处理器
 * 处理游戏菜单、开始游戏、游戏命令等
 */
public class MinesweeperHandler {
    private static final Logger logger = LoggerFactory.getLogger(MinesweeperHandler.class);
    
    private static final String GAME_MENU_TRIGGER = "游戏菜单";
    private static final String GAME_START_PREFIX = "游戏 开始 扫雷";
    private static final String GAME_END_TRIGGER = "游戏 结束 扫雷";
    private static final String GAME_REVEAL_PREFIX = "扫雷 揭开";
    private static final String GAME_FLAG_PREFIX = "扫雷 标记";
    
    // 游戏参数限制
    private static final int MIN_SIZE = 6;
    private static final int MAX_SIZE = 12;
    private static final int MIN_MINES = 8;
    private static final int MAX_MINES = 15;
    
    // 群号 -> 游戏实例（会话锁）
    private final Map<Long, MinesweeperGame> activeGames = new HashMap<>();
    
    private final BiFunction<Long, String, Boolean> messageSender;
    private final BiFunction<Long, String, Boolean> imageSender;
    private final String tempImageDir;
    
    /**
     * 构造函数
     * @param messageSender 消息发送函数
     * @param imageSender 图片发送函数
     * @param tempImageDir 临时图片目录
     */
    public MinesweeperHandler(BiFunction<Long, String, Boolean> messageSender,
                              BiFunction<Long, String, Boolean> imageSender,
                              String tempImageDir) {
        this.messageSender = messageSender;
        this.imageSender = imageSender;
        this.tempImageDir = tempImageDir;
        
        // 确保临时目录存在
        File dir = new File(tempImageDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
    
    /**
     * 检查是否应该处理消息
     */
    public boolean shouldHandle(String messageText) {
        if (messageText == null) {
            return false;
        }
        
        String trimmed = messageText.trim();
        return trimmed.equals(GAME_MENU_TRIGGER) ||
               trimmed.startsWith(GAME_START_PREFIX) ||
               trimmed.equals(GAME_END_TRIGGER) ||
               trimmed.startsWith(GAME_REVEAL_PREFIX) ||
               trimmed.startsWith(GAME_FLAG_PREFIX);
    }
    
    /**
     * 处理游戏消息
     * @param groupId 群号
     * @param userId 用户QQ号
     * @param messageText 消息内容
     */
    public void handleMessage(long groupId, long userId, String messageText) {
        if (messageText == null) {
            return;
        }
        
        String trimmed = messageText.trim();
        
        // 处理游戏菜单
        if (trimmed.equals(GAME_MENU_TRIGGER)) {
            handleGameMenu(groupId, userId);
            return;
        }
        
        // 处理开始游戏
        if (trimmed.startsWith(GAME_START_PREFIX)) {
            handleStartGame(groupId, userId, trimmed);
            return;
        }
        
        // 处理结束游戏
        if (trimmed.equals(GAME_END_TRIGGER)) {
            handleEndGame(groupId, userId);
            return;
        }
        
        // 处理游戏命令（需要先有游戏）
        MinesweeperGame game = activeGames.get(groupId);
        if (game == null) {
            messageSender.apply(groupId, "请先发送\"游戏 开始 扫雷 （大小） （雷数）\"开始游戏");
            return;
        }
        
        // 检查是否是游戏创建者
        if (game.getUserId() != userId) {
            messageSender.apply(groupId, "你不是当前游戏的创建者，无法操作");
            return;
        }
        
        // 处理揭开命令
        if (trimmed.startsWith(GAME_REVEAL_PREFIX)) {
            handleReveal(groupId, userId, trimmed, game);
            return;
        }
        
        // 处理标记命令
        if (trimmed.startsWith(GAME_FLAG_PREFIX)) {
            handleFlag(groupId, userId, trimmed, game);
            return;
        }
    }
    
    /**
     * 处理游戏菜单
     */
    private void handleGameMenu(long groupId, long userId) {
        logger.info("检测到游戏菜单请求，群号: {}, 用户: {}", groupId, userId);
        
        StringBuilder menu = new StringBuilder();
        menu.append("游戏菜单\n\n");
        menu.append("1. 扫雷\n");
        menu.append("   开始：游戏 开始 扫雷 （大小） （雷数）\n");
        menu.append("   结束：游戏 结束 扫雷\n");
        menu.append("   命令：扫雷 揭开 A1 / 扫雷 标记 A1\n\n");
        menu.append("参数说明：\n");
        menu.append("   大小6-12（整数，表示NxN雷场）\n");
        menu.append("   雷数8-15（整数）\n");
        menu.append("   示例：游戏 开始 扫雷 9 10\n");
        
        messageSender.apply(groupId, menu.toString());
    }
    
    /**
     * 处理开始游戏
     * @param command 命令字符串，格式：游戏 开始 扫雷 （大小） （雷数）
     */
    private void handleStartGame(long groupId, long userId, String command) {
        logger.info("检测到开始游戏请求，群号: {}, 用户: {}, 命令: {}", groupId, userId, command);
        
        // 检查是否有正在进行的游戏（会话锁）
        if (activeGames.containsKey(groupId)) {
            MinesweeperGame existingGame = activeGames.get(groupId);
            if (!existingGame.isGameOver() && !existingGame.isGameWon()) {
                messageSender.apply(groupId, "有一位bro正在玩，请等待");
                return;
            } else {
                // 游戏已结束，清理
                activeGames.remove(groupId);
            }
        }
        
        // 解析命令参数
        String[] parts = command.split("\\s+");
        int size = 9;  // 默认大小
        int mineCount = 10;  // 默认雷数
        
        if (parts.length >= 4) {
            try {
                size = Integer.parseInt(parts[3]);
            } catch (NumberFormatException e) {
                messageSender.apply(groupId, "大小参数格式错误，必须是整数");
                return;
            }
        }
        
        if (parts.length >= 5) {
            try {
                mineCount = Integer.parseInt(parts[4]);
            } catch (NumberFormatException e) {
                messageSender.apply(groupId, "雷数参数格式错误，必须是整数");
                return;
            }
        }
        
        // 验证参数范围
        if (size < MIN_SIZE || size > MAX_SIZE) {
            messageSender.apply(groupId, String.format("大小必须在%d-%d之间", MIN_SIZE, MAX_SIZE));
            return;
        }
        
        if (mineCount < MIN_MINES || mineCount > MAX_MINES) {
            messageSender.apply(groupId, String.format("雷数必须在%d-%d之间", MIN_MINES, MAX_MINES));
            return;
        }
        
        // 检查雷数是否超过雷场容量
        int maxPossibleMines = size * size - 1;  // 至少保留一个安全单元格
        if (mineCount > maxPossibleMines) {
            messageSender.apply(groupId, String.format("雷数过多，%dx%d雷场最多只能有%d个雷", size, size, maxPossibleMines));
            return;
        }
        
        // 创建新游戏
        MinesweeperGame game = new MinesweeperGame(groupId, userId, size, mineCount);
        activeGames.put(groupId, game);
        
        // 生成并发送初始游戏图片
        String imagePath = generateAndSaveImage(game);
        if (imagePath != null) {
            imageSender.apply(groupId, imagePath);
        }
        
        messageSender.apply(groupId, String.format("扫雷游戏开始（%dx%d，%d个雷），请使用命令进行\n命令示例：\n扫雷 揭开 A1\n扫雷 标记 A1", size, size, mineCount));
    }
    
    /**
     * 处理结束游戏
     */
    private void handleEndGame(long groupId, long userId) {
        logger.info("检测到结束游戏请求，群号: {}, 用户: {}", groupId, userId);
        
        MinesweeperGame game = activeGames.get(groupId);
        if (game == null) {
            messageSender.apply(groupId, "当前没有进行中的游戏");
            return;
        }
        
        // 检查是否是游戏创建者
        if (game.getUserId() != userId) {
            messageSender.apply(groupId, "你不是当前游戏的创建者，无法结束游戏");
            return;
        }
        
        // 结束游戏
        activeGames.remove(groupId);
        messageSender.apply(groupId, "游戏已结束");
    }
    
    /**
     * 处理揭开命令
     */
    private void handleReveal(long groupId, long userId, String command, MinesweeperGame game) {
        // 解析命令：扫雷 揭开 A1
        String[] parts = command.split("\\s+");
        if (parts.length < 3) {
            messageSender.apply(groupId, "命令格式错误，正确格式：扫雷 揭开 A1");
            return;
        }
        
        String position = parts[2].trim().toUpperCase();
        int[] coords = parsePosition(position, game.getSize());
        if (coords == null) {
            // 检查是否是越界错误
            if (isOutOfBounds(position, game.getSize())) {
                messageSender.apply(groupId, "ERROR：非法越界坐标");
                return;
            }
            char maxCol = (char)('A' + game.getSize() - 1);
            messageSender.apply(groupId, String.format("位置格式错误，正确格式：A1-%c%d", maxCol, game.getSize()));
            return;
        }
        
        // 再次检查边界（双重保险）
        if (coords[0] < 0 || coords[0] >= game.getSize() || coords[1] < 0 || coords[1] >= game.getSize()) {
            messageSender.apply(groupId, "ERROR：非法越界坐标");
            return;
        }
        
        // 检查游戏状态
        if (game.isGameOver()) {
            messageSender.apply(groupId, "游戏已结束（失败），发送\"游戏 开始 扫雷 （大小） （雷数）\"开始新游戏");
            activeGames.remove(groupId);
            return;
        }
        
        if (game.isGameWon()) {
            messageSender.apply(groupId, "恭喜！游戏胜利！发送\"游戏 开始 扫雷 （大小） （雷数）\"开始新游戏");
            activeGames.remove(groupId);
            return;
        }
        
        // 执行揭开（使用try-catch捕获可能的越界异常）
        boolean success;
        try {
            success = game.reveal(coords[0], coords[1]);
        } catch (ArrayIndexOutOfBoundsException e) {
            String errorMessage = buildErrorMessage(e, "reveal", coords[0], coords[1], game.getSize());
            messageSender.apply(groupId, errorMessage);
            logger.warn("坐标越界异常，群号: {}, 用户: {}, 坐标: ({}, {})", groupId, userId, coords[0], coords[1], e);
            return;
        }
        
        if (!success) {
            messageSender.apply(groupId, "无法揭开该位置（可能已揭示或已标记）");
            return;
        }
        
        // 生成并发送游戏图片
        String imagePath = generateAndSaveImage(game);
        if (imagePath != null) {
            imageSender.apply(groupId, imagePath);
        }
        
        // 检查游戏结束
        if (game.isGameOver()) {
            messageSender.apply(groupId, "游戏失败！踩到地雷了！发送\"游戏 开始 扫雷 （大小） （雷数）\"开始新游戏");
            activeGames.remove(groupId);
        } else if (game.isGameWon()) {
            messageSender.apply(groupId, "恭喜！游戏胜利！发送\"游戏 开始 扫雷 （大小） （雷数）\"开始新游戏");
            activeGames.remove(groupId);
        }
    }
    
    /**
     * 处理标记命令
     */
    private void handleFlag(long groupId, long userId, String command, MinesweeperGame game) {
        // 解析命令：扫雷 标记 A1
        String[] parts = command.split("\\s+");
        if (parts.length < 3) {
            messageSender.apply(groupId, "命令格式错误，正确格式：扫雷 标记 A1");
            return;
        }
        
        String position = parts[2].trim().toUpperCase();
        int[] coords = parsePosition(position, game.getSize());
        if (coords == null) {
            // 检查是否是越界错误
            if (isOutOfBounds(position, game.getSize())) {
                messageSender.apply(groupId, "ERROR：非法越界坐标");
                return;
            }
            char maxCol = (char)('A' + game.getSize() - 1);
            messageSender.apply(groupId, String.format("位置格式错误，正确格式：A1-%c%d", maxCol, game.getSize()));
            return;
        }
        
        // 再次检查边界（双重保险）
        if (coords[0] < 0 || coords[0] >= game.getSize() || coords[1] < 0 || coords[1] >= game.getSize()) {
            messageSender.apply(groupId, "ERROR：非法越界坐标");
            return;
        }
        
        // 检查游戏状态
        if (game.isGameOver()) {
            messageSender.apply(groupId, "游戏已结束（失败），发送\"游戏 开始 扫雷 （大小） （雷数）\"开始新游戏");
            activeGames.remove(groupId);
            return;
        }
        
        if (game.isGameWon()) {
            messageSender.apply(groupId, "恭喜！游戏胜利！发送\"游戏 开始 扫雷 （大小） （雷数）\"开始新游戏");
            activeGames.remove(groupId);
            return;
        }
        
        // 执行标记（使用try-catch捕获可能的越界异常）
        boolean success;
        try {
            success = game.toggleFlag(coords[0], coords[1]);
        } catch (ArrayIndexOutOfBoundsException e) {
            String errorMessage = buildErrorMessage(e, "toggleFlag", coords[0], coords[1], game.getSize());
            messageSender.apply(groupId, errorMessage);
            logger.warn("坐标越界异常，群号: {}, 用户: {}, 坐标: ({}, {})", groupId, userId, coords[0], coords[1], e);
            return;
        }
        
        if (!success) {
            messageSender.apply(groupId, "无法标记该位置（可能已揭示）");
            return;
        }
        
        // 生成并发送游戏图片
        String imagePath = generateAndSaveImage(game);
        if (imagePath != null) {
            imageSender.apply(groupId, imagePath);
        }
    }
    
    /**
     * 解析位置字符串（如"A1"）为坐标
     * @param position 位置字符串（A1-...）
     * @param size 雷场大小
     * @return [row, col] 或 null
     */
    private int[] parsePosition(String position, int size) {
        if (position == null || position.length() < 2) {
            return null;
        }
        
        char colChar = position.charAt(0);
        char maxCol = (char)('A' + size - 1);
        if (colChar < 'A' || colChar > maxCol) {
            return null;
        }
        
        int col = colChar - 'A';
        
        try {
            int row = Integer.parseInt(position.substring(1)) - 1;
            if (row < 0 || row >= size) {
                return null;
            }
            
            return new int[]{row, col};
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 检查位置字符串是否越界
     * @param position 位置字符串
     * @param size 雷场大小
     * @return true表示越界，false表示格式错误或其他
     */
    private boolean isOutOfBounds(String position, int size) {
        if (position == null || position.length() < 2) {
            return false; // 格式错误，不是越界
        }
        
        char colChar = position.charAt(0);
        if (colChar < 'A' || colChar > 'Z') {
            return false; // 格式错误，不是越界
        }
        
        // 检查列是否越界
        char maxCol = (char)('A' + size - 1);
        if (colChar > maxCol) {
            return true; // 列越界
        }
        
        try {
            int row = Integer.parseInt(position.substring(1));
            if (row < 1 || row > size) {
                return true; // 行越界
            }
        } catch (NumberFormatException e) {
            return false; // 格式错误，不是越界
        }
        
        return false;
    }
    
    /**
     * 构建错误消息（包含Java堆栈跟踪格式，使用硬编码行号）
     * @param e 异常对象
     * @param methodName 方法名
     * @param row 行坐标
     * @param col 列坐标
     * @param size 雷场大小
     * @return 格式化的错误消息
     */
    private String buildErrorMessage(ArrayIndexOutOfBoundsException e, String methodName, int row, int col, int size) {
        StringBuilder sb = new StringBuilder();
        sb.append("ERROR：非法越界坐标\n\n");
        sb.append(String.format("java.lang.ArrayIndexOutOfBoundsException: Index %d out of bounds for length %d\n\n", 
            Math.max(row, col), size));
        
        // 构建硬编码的堆栈跟踪（模拟真实的崩溃场景）
        if ("reveal".equals(methodName)) {
            sb.append("\tat com.xiaofan.qqbot.game.MinesweeperGame.reveal(MinesweeperGame.java:127)\n");
            sb.append("\tat com.xiaofan.qqbot.handler.MinesweeperHandler.handleReveal(MinesweeperHandler.java:292)\n");
        } else if ("toggleFlag".equals(methodName)) {
            sb.append("\tat com.xiaofan.qqbot.game.MinesweeperGame.toggleFlag(MinesweeperGame.java:197)\n");
            sb.append("\tat com.xiaofan.qqbot.handler.MinesweeperHandler.handleFlag(MinesweeperHandler.java:366)\n");
        }
        sb.append("\tat com.xiaofan.qqbot.handler.MinesweeperHandler.handleMessage(MinesweeperHandler.java:119)\n");
        sb.append("\tat com.xiaofan.qqbot.QQBot$MessageHandler.handleGroupMessageEvent(QQBot.java:577)\n");
        sb.append("\tat com.xiaofan.qqbot.QQBot$MessageHandler.handleWebSocketMessage(QQBot.java:366)\n");
        sb.append("\tat com.xiaofan.qqbot.websocket.NapCatWebSocketClient$1.onMessage(NapCatWebSocketClient.java:694)\n");
        sb.append("\tat okhttp3.internal.ws.RealWebSocket$ReaderRunnable.run(RealWebSocket.java:314)\n");
        sb.append("\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n");
        sb.append("\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)\n");
        sb.append("\tat java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\n");
        sb.append("\tat java.base/java.lang.reflect.Method.invoke(Method.java:568)\n");
        sb.append("\tat org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:205)\n");
        sb.append("\tat org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:150)\n");
        sb.append("\t... 75 more");
        
        return sb.toString();
    }
    
    /**
     * 生成并保存游戏图片
     * @param game 游戏实例
     * @return 图片路径（file://格式）或null
     */
    private String generateAndSaveImage(MinesweeperGame game) {
        try {
            String filename = String.format("minesweeper_%d_%d.png", game.getGroupId(), System.currentTimeMillis());
            String filePath = tempImageDir + File.separator + filename;
            
            boolean success = MinesweeperRenderer.renderGame(game, filePath);
            if (success) {
                return "file://" + filePath.replace("\\", "/");
            }
        } catch (Exception e) {
            logger.error("生成游戏图片失败", e);
        }
        
        return null;
    }
}
