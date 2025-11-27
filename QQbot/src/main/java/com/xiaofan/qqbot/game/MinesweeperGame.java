package com.xiaofan.qqbot.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * 扫雷游戏逻辑类
 * 管理自定义大小雷场的游戏状态
 */
public class MinesweeperGame {
    private static final Logger logger = LoggerFactory.getLogger(MinesweeperGame.class);
    
    // 单元格状态
    public static final int CELL_HIDDEN = 0;      // 未揭开（黄色点）
    public static final int CELL_REVEALED = 1;     // 已揭开（空白或数字）
    public static final int CELL_FLAGGED = 2;      // 标记为地雷（红色点）
    public static final int CELL_MINE = -1;        // 地雷（黑色点）
    
    private final long groupId;
    private final long userId;
    private final int size;                        // 雷场大小（6-12）
    private final int mineCount;                   // 地雷数量（8-15）
    private final int[][] board;          // 雷场：-1表示地雷，0-8表示周围地雷数
    private final int[][] state;          // 单元格状态：0=隐藏，1=已揭示，2=标记
    private final boolean[][] mines;      // 地雷位置
    private boolean gameOver;
    private boolean gameWon;
    private int revealedCount;
    private final int totalCells;
    private final int safeCells;
    
    /**
     * 构造函数
     * @param groupId 群号
     * @param userId 用户QQ号
     * @param size 雷场大小（6-12）
     * @param mineCount 地雷数量（8-15）
     */
    public MinesweeperGame(long groupId, long userId, int size, int mineCount) {
        this.groupId = groupId;
        this.userId = userId;
        this.size = size;
        this.mineCount = mineCount;
        this.board = new int[size][size];
        this.state = new int[size][size];
        this.mines = new boolean[size][size];
        this.gameOver = false;
        this.gameWon = false;
        this.revealedCount = 0;
        this.totalCells = size * size;
        this.safeCells = totalCells - mineCount;
        
        initializeBoard();
        logger.info("创建扫雷游戏，群号: {}, 用户: {}, 大小: {}x{}, 雷数: {}", groupId, userId, size, size, mineCount);
    }
    
    /**
     * 初始化雷场
     */
    private void initializeBoard() {
        // 随机放置地雷
        Random random = new Random();
        int placedMines = 0;
        while (placedMines < mineCount) {
            int row = random.nextInt(size);
            int col = random.nextInt(size);
            if (!mines[row][col]) {
                mines[row][col] = true;
                board[row][col] = CELL_MINE;
                placedMines++;
            }
        }
        
        // 计算每个单元格周围的地雷数
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (!mines[row][col]) {
                    int count = countAdjacentMines(row, col);
                    board[row][col] = count;
                }
            }
        }
    }
    
    /**
     * 计算周围地雷数
     */
    private int countAdjacentMines(int row, int col) {
        int count = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int nr = row + dr;
                int nc = col + dc;
                if (nr >= 0 && nr < size && nc >= 0 && nc < size && mines[nr][nc]) {
                    count++;
                }
            }
        }
        return count;
    }
    
    /**
     * 揭开单元格
     * @param row 行（0到size-1）
     * @param col 列（0到size-1）
     * @return 是否成功，false表示游戏结束或已揭示
     * @throws ArrayIndexOutOfBoundsException 如果坐标越界
     */
    public boolean reveal(int row, int col) {
        if (gameOver || gameWon) {
            return false;
        }
        
        // 检查边界，如果越界则抛出异常（虽然调用方应该已经检查过）
        if (row < 0 || row >= size || col < 0 || col >= size) {
            throw new ArrayIndexOutOfBoundsException(
                String.format("Index (%d, %d) out of bounds for size %d", row, col, size)
            );
        }
        
        if (state[row][col] == CELL_REVEALED || state[row][col] == CELL_FLAGGED) {
            return false; // 已揭示或已标记
        }
        
        // 揭开单元格
        state[row][col] = CELL_REVEALED;
        revealedCount++;
        
        // 检查是否踩到地雷
        if (mines[row][col]) {
            gameOver = true;
            // 显示所有地雷
            for (int r = 0; r < size; r++) {
                for (int c = 0; c < size; c++) {
                    if (mines[r][c] && state[r][c] != CELL_REVEALED) {
                        state[r][c] = CELL_REVEALED; // 显示地雷
                    }
                }
            }
            logger.info("游戏失败，群号: {}, 用户: {}, 位置: ({}, {})", groupId, userId, row, col);
            return true;
        }
        
        // 如果揭开的是空白单元格，自动揭开周围
        if (board[row][col] == 0) {
            revealAdjacentCells(row, col);
        }
        
        // 检查是否胜利
        if (revealedCount == safeCells) {
            gameWon = true;
            logger.info("游戏胜利，群号: {}, 用户: {}", groupId, userId);
        }
        
        return true;
    }
    
    /**
     * 递归揭开周围的空白单元格
     */
    private void revealAdjacentCells(int row, int col) {
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int nr = row + dr;
                int nc = col + dc;
                if (nr >= 0 && nr < size && nc >= 0 && nc < size) {
                    if (state[nr][nc] == CELL_HIDDEN && !mines[nr][nc]) {
                        state[nr][nc] = CELL_REVEALED;
                        revealedCount++;
                        if (board[nr][nc] == 0) {
                            revealAdjacentCells(nr, nc);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 标记/取消标记单元格
     * @param row 行（0到size-1）
     * @param col 列（0到size-1）
     * @return 是否成功
     * @throws ArrayIndexOutOfBoundsException 如果坐标越界
     */
    public boolean toggleFlag(int row, int col) {
        if (gameOver || gameWon) {
            return false;
        }
        
        // 检查边界，如果越界则抛出异常（虽然调用方应该已经检查过）
        if (row < 0 || row >= size || col < 0 || col >= size) {
            throw new ArrayIndexOutOfBoundsException(
                String.format("Index (%d, %d) out of bounds for size %d", row, col, size)
            );
        }
        
        if (state[row][col] == CELL_REVEALED) {
            return false; // 已揭示的不能标记
        }
        
        // 切换标记状态
        if (state[row][col] == CELL_FLAGGED) {
            state[row][col] = CELL_HIDDEN;
        } else {
            state[row][col] = CELL_FLAGGED;
        }
        
        return true;
    }
    
    /**
     * 获取单元格显示值（用于渲染）
     * @param row 行
     * @param col 列
     * @return 显示值：-2=隐藏（黄色点），-1=地雷（黑色点），0-8=数字，-3=标记（红色点）
     */
    public int getCellDisplayValue(int row, int col) {
        if (state[row][col] == CELL_FLAGGED) {
            return -3; // 标记（红色点）
        }
        if (state[row][col] == CELL_HIDDEN) {
            return -2; // 隐藏（黄色点）
        }
        if (state[row][col] == CELL_REVEALED) {
            if (mines[row][col]) {
                return -1; // 地雷（黑色点）
            }
            return board[row][col]; // 数字或空白（0-8）
        }
        return -2;
    }
    
    // Getters
    public long getGroupId() {
        return groupId;
    }
    
    public long getUserId() {
        return userId;
    }
    
    public boolean isGameOver() {
        return gameOver;
    }
    
    public boolean isGameWon() {
        return gameWon;
    }
    
    public int getRevealedCount() {
        return revealedCount;
    }
    
    public int getSafeCells() {
        return safeCells;
    }
    
    public int getSize() {
        return size;
    }
    
    public int getMineCount() {
        return mineCount;
    }
}
