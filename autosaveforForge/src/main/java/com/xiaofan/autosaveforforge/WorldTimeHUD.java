package com.xiaofan.autosaveforforge;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 世界时间 HUD 显示
 * 在屏幕顶部中间显示服务器世界时间
 */
@Mod.EventBusSubscriber(modid = Autosaveforforge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class WorldTimeHUD {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        // 在所有HUD元素渲染完成后渲染时间显示
        // 使用 CROSSHAIR 层作为触发点（这个层在游戏界面中总是渲染）
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        
        // 只在游戏界面显示，不在菜单界面显示
        if (mc.screen != null) {
            return;
        }
        
        // 获取服务器世界时间（不是客户端本地时间）
        // getDayTime() 返回的是服务器同步的世界时间
        long worldTime = mc.level.getDayTime();
        
        // 计算游戏内时间
        // Minecraft 一天 = 24000 刻
        // 游戏内时间从 0 刻（6:00）开始
        long dayTime = worldTime % 24000;
        
        // 计算小时和分钟
        // 游戏内时间：0刻 = 6:00, 1000刻 = 7:00, 6000刻 = 12:00, 18000刻 = 18:00
        int hours = (int) ((dayTime / 1000 + 6) % 24);
        int minutes = (int) ((dayTime % 1000) * 60 / 1000);
        
        // 格式化时间显示：00:00，xx刻
        String timeString = String.format("%02d:%02d", hours, minutes);
        String tickString = String.format("%d刻", dayTime);
        String displayText = timeString + "，" + tickString;
        
        // 获取屏幕尺寸
        int screenWidth = event.getWindow().getGuiScaledWidth();
        
        // 计算文本宽度以居中显示
        int textWidth = mc.font.width(displayText);
        int x = (screenWidth - textWidth) / 2;
        int y = 10; // 距离顶部10像素
        
        // 渲染文本
        GuiGraphics guiGraphics = event.getGuiGraphics();
        
        // 绘制半透明背景
        int padding = 4;
        int bgColor = 0x80000000; // 半透明黑色 (ARGB: 80 = 50% 透明度)
        guiGraphics.fill(x - padding, y - padding, x + textWidth + padding, y + mc.font.lineHeight + padding, bgColor);
        
        // 绘制文本（白色）
        guiGraphics.drawString(mc.font, displayText, x, y, 0xFFFFFF, false);
    }
}

