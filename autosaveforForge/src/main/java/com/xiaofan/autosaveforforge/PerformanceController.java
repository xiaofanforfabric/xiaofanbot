package com.xiaofan.autosaveforforge;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * 性能控制器
 * Home 键：切换分辨率到 320x240（再次按下恢复）
 * End 键：切换帧率限制到 10FPS（开关模式，原版最低限制）
 * F9 键：释放/锁定鼠标（开关模式）
 */
@Mod.EventBusSubscriber(modid = Autosaveforforge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PerformanceController {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // 分辨率控制
    private static boolean isLowResolutionMode = false;
    private static int originalWidth = -1;
    private static int originalHeight = -1;
    private static final int LOW_RES_WIDTH = 320;
    private static final int LOW_RES_HEIGHT = 240;
    
    // 帧率控制
    private static boolean isLowFPSMode = false;
    private static int originalMaxFPS = -1;
    private static final int LOW_FPS = 10; // 原版最低只能限制到 10 FPS
    
    // 鼠标控制
    private static boolean isMouseReleased = false;
    
    // 按键状态跟踪（避免重复触发）
    private static boolean homeKeyPressed = false;
    private static boolean endKeyPressed = false;
    private static boolean f9KeyPressed = false;
    
    /**
     * 监听客户端 Tick 事件来检测按键
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        
        // 如果屏幕打开（GUI），重置按键状态（但 F9 键仍然可以工作）
        if (mc.screen != null) {
            homeKeyPressed = false;
            endKeyPressed = false;
            // F9 键在 GUI 打开时也可以工作，用于锁定鼠标
        }
        
        // 检测 Home 键
        long windowHandle = mc.getWindow().getWindow();
        boolean homeKeyDown = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_HOME) == GLFW.GLFW_PRESS;
        if (homeKeyDown && !homeKeyPressed) {
            homeKeyPressed = true;
            toggleResolution(mc);
        } else if (!homeKeyDown) {
            homeKeyPressed = false;
        }
        
        // 检测 End 键
        boolean endKeyDown = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_END) == GLFW.GLFW_PRESS;
        if (endKeyDown && !endKeyPressed) {
            endKeyPressed = true;
            toggleFPSLimit(mc);
        } else if (!endKeyDown) {
            endKeyPressed = false;
        }
        
        // 检测 F9 键（无论是否在 GUI 中都可以使用）
        boolean f9KeyDown = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_F9) == GLFW.GLFW_PRESS;
        if (f9KeyDown && !f9KeyPressed) {
            f9KeyPressed = true;
            toggleMouseGrab(mc);
        } else if (!f9KeyDown) {
            f9KeyPressed = false;
        }
    }
    
    /**
     * 切换分辨率
     */
    private static void toggleResolution(Minecraft mc) {
        mc.execute(() -> {
            try {
                if (!isLowResolutionMode) {
                    // 保存原始分辨率
                    if (originalWidth == -1 || originalHeight == -1) {
                        originalWidth = mc.getWindow().getWidth();
                        originalHeight = mc.getWindow().getHeight();
                        LOGGER.info("[性能控制] 保存原始分辨率: {}x{}", originalWidth, originalHeight);
                    }
                    
                    // 切换到低分辨率
                    mc.getWindow().setWindowed(LOW_RES_WIDTH, LOW_RES_HEIGHT);
                    isLowResolutionMode = true;
                    LOGGER.info("[性能控制] 分辨率已切换到: {}x{}", LOW_RES_WIDTH, LOW_RES_HEIGHT);
                    
                    // 显示提示消息
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[性能控制] 分辨率已切换到 320x240"));
                    }
                } else {
                    // 恢复原始分辨率
                    if (originalWidth > 0 && originalHeight > 0) {
                        mc.getWindow().setWindowed(originalWidth, originalHeight);
                        LOGGER.info("[性能控制] 分辨率已恢复到: {}x{}", originalWidth, originalHeight);
                        
                        // 显示提示消息
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[性能控制] 分辨率已恢复到 " + originalWidth + "x" + originalHeight));
                        }
                    } else {
                        // 如果没有保存的原始分辨率，使用默认值
                        mc.getWindow().setWindowed(854, 480);
                        LOGGER.info("[性能控制] 分辨率已恢复到默认值: 854x480");
                        
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[性能控制] 分辨率已恢复到默认值"));
                        }
                    }
                    isLowResolutionMode = false;
                }
            } catch (Exception e) {
                LOGGER.error("[性能控制] 切换分辨率失败: {}", e.getMessage(), e);
            }
        });
    }
    
    /**
     * 切换帧率限制
     */
    private static void toggleFPSLimit(Minecraft mc) {
        mc.execute(() -> {
            try {
                if (!isLowFPSMode) {
                    // 保存原始帧率限制
                    if (originalMaxFPS == -1) {
                        originalMaxFPS = mc.options.framerateLimit().get();
                        LOGGER.info("[性能控制] 保存原始帧率限制: {} FPS", originalMaxFPS);
                    }
                    
                    // 切换到低帧率（原版最低只能到 10 FPS）
                    mc.options.framerateLimit().set(LOW_FPS);
                    isLowFPSMode = true;
                    LOGGER.info("[性能控制] 帧率限制已切换到: {} FPS (原版最低限制)", LOW_FPS);
                    
                    // 显示提示消息
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[性能控制] 帧率限制已切换到 " + LOW_FPS + " FPS (原版最低限制)"));
                    }
                } else {
                    // 恢复原始帧率限制
                    if (originalMaxFPS > 0) {
                        mc.options.framerateLimit().set(originalMaxFPS);
                        LOGGER.info("[性能控制] 帧率限制已恢复到: {} FPS", originalMaxFPS);
                        
                        // 显示提示消息
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[性能控制] 帧率限制已恢复到 " + originalMaxFPS + " FPS"));
                        }
                    } else {
                        // 如果没有保存的原始帧率限制，使用默认值（通常是 60）
                        mc.options.framerateLimit().set(60);
                        LOGGER.info("[性能控制] 帧率限制已恢复到默认值: 60 FPS");
                        
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[性能控制] 帧率限制已恢复到默认值"));
                        }
                    }
                    isLowFPSMode = false;
                }
            } catch (Exception e) {
                LOGGER.error("[性能控制] 切换帧率限制失败: {}", e.getMessage(), e);
            }
        });
    }
    
    /**
     * 获取当前分辨率模式
     */
    public static boolean isLowResolutionMode() {
        return isLowResolutionMode;
    }
    
    /**
     * 获取当前帧率限制模式
     */
    public static boolean isLowFPSMode() {
        return isLowFPSMode;
    }
    
    /**
     * 切换鼠标锁定状态
     */
    private static void toggleMouseGrab(Minecraft mc) {
        mc.execute(() -> {
            try {
                if (!isMouseReleased) {
                    // 释放鼠标（显示鼠标光标）
                    mc.mouseHandler.releaseMouse();
                    isMouseReleased = true;
                    LOGGER.info("[性能控制] 鼠标已释放（显示鼠标光标）");
                    
                    // 显示提示消息
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[性能控制] 鼠标已释放（显示鼠标光标）"));
                    }
                } else {
                    // 锁定鼠标（隐藏鼠标光标，进入游戏模式）
                    mc.mouseHandler.grabMouse();
                    isMouseReleased = false;
                    LOGGER.info("[性能控制] 鼠标已锁定（隐藏鼠标光标）");
                    
                    // 显示提示消息
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[性能控制] 鼠标已锁定（隐藏鼠标光标）"));
                    }
                }
            } catch (Exception e) {
                LOGGER.error("[性能控制] 切换鼠标锁定状态失败: {}", e.getMessage(), e);
            }
        });
    }
    
    /**
     * 获取当前鼠标是否已释放
     */
    public static boolean isMouseReleased() {
        return isMouseReleased;
    }
    
    /**
     * 窗口焦点事件监听器
     * 防止窗口失去焦点时自动显示暂停菜单
     */
    @Mod.EventBusSubscriber(modid = Autosaveforforge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class WindowFocusHandler {
        private static boolean wasWindowFocused = true;
        
        /**
         * 监听屏幕打开事件，如果是因为窗口失去焦点而打开的暂停菜单，则关闭它
         */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onScreenOpen(ScreenEvent.Opening event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                return;
            }
            
            // 检查是否是暂停菜单
            if (event.getNewScreen() instanceof PauseScreen) {
                // 检查窗口是否失去焦点
                boolean isWindowFocused = GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
                
                // 如果窗口失去焦点，且之前是焦点状态，则阻止显示暂停菜单
                if (!isWindowFocused && wasWindowFocused && mc.level != null && !mc.isPaused()) {
                    event.setCanceled(true);
                    LOGGER.debug("[性能控制] 阻止因窗口失去焦点而显示暂停菜单");
                }
            }
            
            // 更新窗口焦点状态
            wasWindowFocused = GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
        }
    }
}

