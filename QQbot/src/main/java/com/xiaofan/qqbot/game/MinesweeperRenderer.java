package com.xiaofan.qqbot.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 扫雷游戏图片渲染器
 * 使用Java 2D生成游戏雷场图片
 */
public class MinesweeperRenderer {
    private static final Logger logger = LoggerFactory.getLogger(MinesweeperRenderer.class);
    
    private static final int CELL_SIZE = 40; // 每个单元格大小
    private static final int LABEL_SIZE = 30; // 标签区域大小
    private static final int PADDING = 10; // 内边距
    private static final int BORDER_WIDTH = 2; // 边框宽度
    
    // 颜色定义
    private static final Color COLOR_BACKGROUND = new Color(240, 240, 240);
    private static final Color COLOR_CELL_BG = new Color(255, 255, 255);
    private static final Color COLOR_CELL_HIDDEN = new Color(255, 255, 0); // 黄色
    private static final Color COLOR_CELL_FLAGGED = new Color(255, 0, 0); // 红色
    private static final Color COLOR_CELL_MINE = new Color(0, 0, 0); // 黑色
    private static final Color COLOR_BORDER = new Color(100, 100, 100);
    private static final Color COLOR_TEXT = new Color(0, 0, 0);
    private static final Color COLOR_LABEL = new Color(50, 50, 50);
    
    // 数字颜色
    private static final Color[] NUMBER_COLORS = {
        new Color(0, 0, 255),      // 1 - 蓝色
        new Color(0, 128, 0),      // 2 - 绿色
        new Color(255, 0, 0),      // 3 - 红色
        new Color(0, 0, 128),      // 4 - 深蓝
        new Color(128, 0, 0),      // 5 - 深红
        new Color(0, 128, 128),    // 6 - 青色
        new Color(0, 0, 0),        // 7 - 黑色
        new Color(128, 128, 128)   // 8 - 灰色
    };
    
    /**
     * 获取系统可用字体（headless模式兼容）
     * @param style 字体样式
     * @param size 字体大小
     * @return Font对象
     */
    private static Font getSystemFont(int style, int size) {
        // 在headless模式下，使用逻辑字体名称更可靠
        // 优先尝试SansSerif（在headless模式下最稳定）
        try {
            return new Font(Font.SANS_SERIF, style, size);
        } catch (Exception e) {
            // 如果失败，尝试使用默认字体
            return new Font(Font.DIALOG, style, size);
        }
    }
    
    /**
     * 渲染游戏雷场图片
     * 使用纯Java 2D在headless模式下生成图片，无需图形界面
     * @param game 游戏实例
     * @param outputPath 输出文件路径
     * @return 是否成功
     */
    public static boolean renderGame(MinesweeperGame game, String outputPath) {
        try {
            int size = game.getSize();
            int width = LABEL_SIZE + PADDING * 2 + size * CELL_SIZE + PADDING * 2;
            int height = LABEL_SIZE + PADDING * 2 + size * CELL_SIZE + PADDING * 2;
            
            // 创建BufferedImage（在headless模式下完全支持）
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();
            
            try {
                // 启用抗锯齿
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                
                // 绘制背景
                g2d.setColor(COLOR_BACKGROUND);
                g2d.fillRect(0, 0, width, height);
                
                // 绘制列标签（A-...）
                g2d.setColor(COLOR_LABEL);
                Font labelFont = getSystemFont(Font.BOLD, 20);
                g2d.setFont(labelFont);
                FontMetrics fm = g2d.getFontMetrics();
                int labelX = LABEL_SIZE + PADDING;
                for (int col = 0; col < size; col++) {
                    String label = String.valueOf((char)('A' + col));
                    int labelWidth = fm.stringWidth(label);
                    int x = labelX + col * CELL_SIZE + (CELL_SIZE - labelWidth) / 2;
                    g2d.drawString(label, x, LABEL_SIZE - 5);
                }
                
                // 绘制行标签（1-...）
                int labelY = LABEL_SIZE + PADDING;
                for (int row = 0; row < size; row++) {
                    String label = String.valueOf(row + 1);
                    int labelWidth = fm.stringWidth(label);
                    int x = (LABEL_SIZE - labelWidth) / 2;
                    int y = labelY + row * CELL_SIZE + CELL_SIZE / 2 + 7;
                    g2d.drawString(label, x, y);
                }
                
                // 绘制雷场
                int startX = LABEL_SIZE + PADDING;
                int startY = LABEL_SIZE + PADDING;
                
                for (int row = 0; row < size; row++) {
                    for (int col = 0; col < size; col++) {
                        int x = startX + col * CELL_SIZE;
                        int y = startY + row * CELL_SIZE;
                        
                        int displayValue = game.getCellDisplayValue(row, col);
                        
                        // 绘制单元格背景
                        g2d.setColor(COLOR_CELL_BG);
                        g2d.fillRect(x, y, CELL_SIZE, CELL_SIZE);
                        
                        // 绘制边框
                        g2d.setColor(COLOR_BORDER);
                        g2d.setStroke(new BasicStroke(BORDER_WIDTH));
                        g2d.drawRect(x, y, CELL_SIZE, CELL_SIZE);
                        
                        // 绘制单元格内容
                        if (displayValue == -2) {
                            // 隐藏（黄色点）
                            g2d.setColor(COLOR_CELL_HIDDEN);
                            int dotSize = 12;
                            int dotX = x + (CELL_SIZE - dotSize) / 2;
                            int dotY = y + (CELL_SIZE - dotSize) / 2;
                            g2d.fillOval(dotX, dotY, dotSize, dotSize);
                        } else if (displayValue == -3) {
                            // 标记（红色点）
                            g2d.setColor(COLOR_CELL_FLAGGED);
                            int dotSize = 12;
                            int dotX = x + (CELL_SIZE - dotSize) / 2;
                            int dotY = y + (CELL_SIZE - dotSize) / 2;
                            g2d.fillOval(dotX, dotY, dotSize, dotSize);
                        } else if (displayValue == -1) {
                            // 地雷（黑色点）
                            g2d.setColor(COLOR_CELL_MINE);
                            int dotSize = 16;
                            int dotX = x + (CELL_SIZE - dotSize) / 2;
                            int dotY = y + (CELL_SIZE - dotSize) / 2;
                            g2d.fillOval(dotX, dotY, dotSize, dotSize);
                        } else if (displayValue >= 0) {
                            // 数字或空白
                            if (displayValue > 0) {
                                // 绘制数字（使用headless兼容字体）
                                Font numberFont = getSystemFont(Font.BOLD, 24);
                                g2d.setFont(numberFont);
                                FontMetrics numFm = g2d.getFontMetrics();
                                String numStr = String.valueOf(displayValue);
                                int numWidth = numFm.stringWidth(numStr);
                                int numX = x + (CELL_SIZE - numWidth) / 2;
                                int numY = y + (CELL_SIZE + numFm.getAscent()) / 2 - 5;
                                
                                // 根据数字选择颜色
                                if (displayValue >= 1 && displayValue <= 8) {
                                    g2d.setColor(NUMBER_COLORS[displayValue - 1]);
                                } else {
                                    g2d.setColor(COLOR_TEXT);
                                }
                                
                                g2d.drawString(numStr, numX, numY);
                            }
                            // 空白单元格（displayValue == 0）不需要绘制内容
                        }
                    }
                }
            } finally {
                // 确保Graphics2D资源被释放
                g2d.dispose();
            }
            
            // 保存图片
            File outputFile = new File(outputPath);
            outputFile.getParentFile().mkdirs();
            ImageIO.write(image, "png", outputFile);
            
            logger.info("游戏图片已生成: {}", outputPath);
            return true;
        } catch (Exception e) {
            logger.error("生成游戏图片失败", e);
            return false;
        }
    }
}
