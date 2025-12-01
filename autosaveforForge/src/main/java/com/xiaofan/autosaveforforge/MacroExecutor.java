package com.xiaofan.autosaveforforge;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 宏执行器
 * 执行宏命令
 */
public class MacroExecutor implements Runnable {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private final String macroName;
    private final Macro macro;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private int currentCommandIndex = 0;
    
    public MacroExecutor(String macroName, Macro macro) {
        this.macroName = macroName;
        this.macro = macro;
    }
    
    @Override
    public void run() {
        logToChatAndLogger("[宏执行] 开始执行宏: " + macroName);
        LOGGER.info("[宏执行] 开始执行宏: {}", macroName);
        
        try {
            executeMacro(macro);
        } catch (Exception e) {
            logToChatAndLogger("[宏执行] 执行宏时出错: " + macroName + " - " + e.getMessage());
            LOGGER.error("[宏执行] 执行宏时出错: {}", macroName, e);
        } finally {
            logToChatAndLogger("[宏执行] 宏执行结束: " + macroName);
            LOGGER.info("[宏执行] 宏执行结束: {}", macroName);
            
            // 从运行列表中移除，确保状态同步
            BaritoneTaskManager.getInstance().removeRunningMacro(macroName);
        }
    }
    
    /**
     * 同时记录到日志和聊天框
     */
    private void logToChatAndLogger(String message) {
        LOGGER.info(message);
        
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            // 在主游戏线程中显示消息
            mc.execute(() -> {
                if (mc.player != null) {
                    // 使用系统消息，不会发送到服务器
                    mc.player.sendSystemMessage(Component.literal("§7[宏] §r" + message));
                }
            });
        }
    }
    
    /**
     * 执行宏
     * 如果宏包含 if 条件，需要循环执行以持续检查条件
     */
    private void executeMacro(Macro macro) {
        String macroInfo = String.format("[宏执行] 宏包含 %d 个命令", macro.commands.size());
        logToChatAndLogger(macroInfo);
        LOGGER.info("[宏执行] 宏包含 {} 个命令", macro.commands.size());
        if (macro.commands.isEmpty()) {
            logToChatAndLogger("[宏执行] 宏文件没有解析出任何命令，请检查宏文件格式");
            LOGGER.warn("[宏执行] 宏文件没有解析出任何命令，请检查宏文件格式");
            return;
        }
        
        // 检查是否包含 if 条件（需要循环执行）
        boolean hasConditional = macro.commands.stream()
            .anyMatch(cmd -> cmd instanceof IfStatement);
        
        if (hasConditional) {
            // 包含条件语句，需要循环执行
            logToChatAndLogger("[宏执行] 宏包含条件语句，将循环执行");
            LOGGER.info("[宏执行] 宏包含条件语句，将循环执行");
            while (!stopped.get()) {
                for (int i = 0; i < macro.commands.size(); i++) {
                    if (stopped.get()) {
                        LOGGER.info("[宏执行] 宏已被停止，停止执行剩余命令");
                        return;
                    }
                    MacroCommand cmd = macro.commands.get(i);
                    String cmdInfo = String.format("[宏执行] 执行第 %d 个命令: %s", i + 1, cmd.getClass().getSimpleName());
                    logToChatAndLogger(cmdInfo);
                    LOGGER.info("[宏执行] 执行第 {} 个命令: {}", i + 1, cmd.getClass().getSimpleName());
                    try {
                        cmd.execute(this);
                        // 如果是条件语句，等待一小段时间再检查
                        if (cmd instanceof IfStatement) {
                            try {
                                Thread.sleep(100); // 等待100ms再检查条件
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error("[宏执行] 执行命令时出错", e);
                    }
                }
                // 每次循环之间等待一段时间
                try {
                    Thread.sleep(500); // 等待500ms再进行下一轮检查
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        } else {
            // 不包含条件语句，一次性执行完
            for (int i = 0; i < macro.commands.size(); i++) {
                if (stopped.get()) {
                    LOGGER.info("[宏执行] 宏已被停止，停止执行剩余命令");
                    break;
                }
                MacroCommand cmd = macro.commands.get(i);
                LOGGER.info("[宏执行] 执行第 {} 个命令: {}", i + 1, cmd.getClass().getSimpleName());
                try {
                    cmd.execute(this);
                } catch (Exception e) {
                    LOGGER.error("[宏执行] 执行命令时出错", e);
                }
            }
        }
    }
    
    /**
     * 执行 do 命令
     */
    public void executeDoCommand(String content) {
        if (stopped.get()) {
            return;
        }
        
        // 检查是否是函数调用 do fun "name";
        if (content.startsWith("fun ")) {
            String funcName = content.substring(4).trim();
            if (funcName.startsWith("\"") && funcName.endsWith("\"")) {
                funcName = funcName.substring(1, funcName.length() - 1);
            } else if (funcName.endsWith("\"")) {
                // 处理 do fun "name"; 格式
                int quoteStart = funcName.indexOf("\"");
                if (quoteStart >= 0) {
                    funcName = funcName.substring(quoteStart + 1, funcName.length() - 1);
                }
            }
            callFunction(funcName);
            return;
        }
        
        // 检查是否是 end 命令
        // 注意：end 命令只停止当前执行上下文，不会阻止后续命令的执行
        // 只有当在 if/else 分支中执行 end 时，才会停止该分支的执行
        if (content.equals("end")) {
            logToChatAndLogger("[宏执行] 执行 end 命令，停止当前执行上下文");
            LOGGER.info("[宏执行] 执行 end 命令，停止当前执行上下文");
            stop();
            return;
        }
        
        // 检查是否是 wait 命令
        if (content.startsWith("wait")) {
            String waitContent = content.substring(4).trim();
            executeWaitCommand(waitContent);
            return;
        }
        
        // 检查是否是原版命令（/开头）
        if (content.startsWith("/")) {
            String command = content.substring(1).trim(); // 去掉 / 前缀
            executeMinecraftCommand(command);
            return;
        }
        
        // 检查是否是 baritone 命令（#开头）
        if (content.startsWith("#")) {
            String baritoneCmd = content.substring(1).trim();
            // 检查是否是阻塞命令（需要等待执行完成）
            boolean isBlocking = isBlockingCommand(baritoneCmd);
            if (isBlocking) {
                logToChatAndLogger("[宏执行] 检测到阻塞命令: " + baritoneCmd + "，将等待执行完成");
            LOGGER.info("[宏执行] 检测到阻塞命令: {}，将等待执行完成", baritoneCmd);
                executeBaritoneCommandBlocking(baritoneCmd);
            } else {
                executeBaritoneCommand(baritoneCmd);
            }
        } else {
            LOGGER.warn("[宏执行] 未知命令: {}", content);
        }
    }
    
    /**
     * 执行原版 Minecraft 命令
     * @param command 命令内容（不包含 / 前缀）
     */
    private void executeMinecraftCommand(String command) {
        if (stopped.get()) {
            return;
        }
        
        logToChatAndLogger("[宏执行] 准备执行原版命令: /" + command);
        LOGGER.info("[宏执行] 准备执行原版命令: /{}", command);
        
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            LOGGER.warn("[宏执行] 无法执行原版命令，玩家未初始化");
            return;
        }
        
        if (mc.getConnection() == null) {
            LOGGER.warn("[宏执行] 无法执行原版命令，未连接到服务器");
            return;
        }
        
        // 确保在主游戏线程中执行
        if (mc.isSameThread()) {
            // 已经在主线程，直接执行
            executeMinecraftCommandInternal(command);
        } else {
            // 不在主线程，切换到主线程执行
            final String cmd = command;
            mc.execute(() -> executeMinecraftCommandInternal(cmd));
        }
    }
    
    /**
     * 内部方法：实际执行原版命令
     */
    private void executeMinecraftCommandInternal(String command) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getConnection() == null || mc.player == null) {
                LOGGER.warn("[宏执行] 无法执行原版命令，连接或玩家未初始化");
                return;
            }
            
            // 发送命令到服务器（sendCommand 不需要 / 前缀）
            mc.getConnection().sendCommand(command);
            
            logToChatAndLogger("[宏执行] ✓ 已发送原版命令: /" + command);
            LOGGER.info("[宏执行] ✓ 已发送原版命令: /{}", command);
            
        } catch (Exception e) {
            LOGGER.error("[宏执行] 执行原版命令时出错: /{}", command, e);
            logToChatAndLogger("[宏执行] 执行原版命令时出错: /" + command + " - " + e.getMessage());
        }
    }
    
    /**
     * 调用函数
     * 函数内部可以访问宏的所有函数定义，支持递归调用
     */
    private void callFunction(String funcName) {
        logToChatAndLogger("[宏执行] 尝试调用函数: " + funcName);
        logToChatAndLogger("[宏执行] 当前宏包含的函数: " + macro.functions.keySet());
        LOGGER.info("[宏执行] 尝试调用函数: {}", funcName);
        LOGGER.info("[宏执行] 当前宏包含的函数: {}", macro.functions.keySet());
        
        Function func = macro.functions.get(funcName);
        if (func == null) {
            logToChatAndLogger("[宏执行] 函数不存在: " + funcName + "，可用函数: " + macro.functions.keySet());
            LOGGER.warn("[宏执行] 函数不存在: {}，可用函数: {}", funcName, macro.functions.keySet());
            return;
        }
        
            String funcInfo = String.format("[宏执行] 调用函数: %s (后台执行: %s)，函数包含 %d 个命令", 
                funcName, func.isBackground, func.commands.size());
            logToChatAndLogger(funcInfo);
            LOGGER.info("[宏执行] 调用函数: {} (后台执行: {})，函数包含 {} 个命令", funcName, func.isBackground, func.commands.size());
        
        // 确保函数可以访问宏的所有函数定义（用于递归调用）
        func.functions = macro.functions;
        
        if (func.isBackground) {
            // 后台执行（不阻塞主线程）
            new Thread(() -> {
                LOGGER.info("[宏执行] 函数 {} 在后台线程开始执行", funcName);
                executeFunctionCommands(func);
                LOGGER.info("[宏执行] 函数 {} 在后台线程执行完成", funcName);
            }, "MacroFunction-" + funcName).start();
        } else {
            // 主线程执行
            LOGGER.info("[宏执行] 函数 {} 在主线程执行", funcName);
            executeFunctionCommands(func);
        }
    }
    
    /**
     * 执行函数命令
     */
    private void executeFunctionCommands(Function func) {
        // 检查函数是否包含条件语句（需要循环执行）
        boolean hasConditional = func.commands.stream()
            .anyMatch(cmd -> cmd instanceof IfStatement);
        
        if (hasConditional) {
            // 包含条件语句，需要循环执行
            LOGGER.info("[宏执行] 函数 {} 包含条件语句，将循环执行", func.name);
            while (!stopped.get()) {
                for (MacroCommand cmd : func.commands) {
                    if (stopped.get()) {
                        return;
                    }
                    try {
                        cmd.execute(this);
                        // 如果是条件语句，等待一小段时间再检查
                        if (cmd instanceof IfStatement) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error("[宏执行] 函数 {} 执行命令时出错", func.name, e);
                    }
                }
                // 每次循环之间等待一段时间
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        } else {
            // 不包含条件语句，一次性执行完
            for (MacroCommand cmd : func.commands) {
                if (stopped.get()) {
                    break;
                }
                try {
                    cmd.execute(this);
                } catch (Exception e) {
                    LOGGER.error("[宏执行] 函数 {} 执行命令时出错", func.name, e);
                }
            }
        }
    }
    
    /**
     * 检查命令是否是阻塞命令（需要等待执行完成）
     */
    private boolean isBlockingCommand(String command) {
        if (command == null || command.isEmpty()) {
            return false;
        }
        String cmd = command.trim().toLowerCase();
        // 阻塞命令列表：goto, mine, explore, follow, farm 等需要等待完成的命令
        return cmd.startsWith("goto ") || 
               cmd.startsWith("mine ") || 
               cmd.startsWith("explore") ||
               cmd.startsWith("follow ") ||
               cmd.startsWith("farm");
    }
    
    /**
     * 执行阻塞的 baritone 命令，等待执行完成
     */
    private void executeBaritoneCommandBlocking(String command) {
        LOGGER.info("[宏执行] 执行阻塞 Baritone 命令: {}", command);
        
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            LOGGER.warn("[宏执行] 无法执行 baritone 命令，玩家未初始化");
            return;
        }
        
        try {
            // 获取 Baritone 实例
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone == null) {
                LOGGER.warn("[宏执行] Baritone 未加载，无法执行命令");
                return;
            }
            
            // 解析命令以获取目标坐标（用于 goto 命令）
            BlockPos targetPos = null;
            String cmd = command.trim().toLowerCase();
            if (cmd.startsWith("goto ")) {
                String[] parts = command.trim().split("\\s+");
                if (parts.length >= 4) {
                    try {
                        int x = Integer.parseInt(parts[1]);
                        int y = Integer.parseInt(parts[2]);
                        int z = Integer.parseInt(parts[3]);
                        targetPos = new BlockPos(x, y, z);
                        LOGGER.info("[宏执行] 解析到目标坐标: ({}, {}, {})", x, y, z);
                    } catch (NumberFormatException e) {
                        LOGGER.warn("[宏执行] 无法解析 goto 命令的坐标: {}", command);
                    }
                }
            }
            
            // 在主游戏线程中执行命令
            if (mc.isSameThread()) {
                executeBaritoneCommandInternal(command);
            } else {
                final String cmdFinal = command;
                mc.execute(() -> executeBaritoneCommandInternal(cmdFinal));
                
                // 等待命令被提交
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            
            // 等待命令执行完成
            waitForBaritoneCommand(baritone, command, targetPos);
            
        } catch (Exception e) {
            LOGGER.error("[宏执行] 执行阻塞 Baritone 命令时出错: {}", command, e);
            e.printStackTrace();
        } finally {
            // 命令执行完成，清除命令记录
            BaritoneTaskManager.getInstance().clearMacroCommand(macroName);
        }
    }
    
    /**
     * 等待 Baritone 命令执行完成
     */
    private void waitForBaritoneCommand(IBaritone baritone, String command, BlockPos targetPos) {
        LOGGER.info("[宏执行] 等待 Baritone 命令执行完成: {}", command);
        
        String cmd = command.trim().toLowerCase();
        
        // 根据命令类型选择不同的等待策略
        if (cmd.startsWith("goto ")) {
            // 等待路径查找完成
            try {
                waitForPathfinding(baritone, targetPos);
            } finally {
                // 路径查找完成，清除命令记录
                BaritoneTaskManager.getInstance().clearMacroCommand(macroName);
            }
        } else if (cmd.startsWith("mine ")) {
            // 等待挖矿完成（通常需要手动停止或挖完目标）
            waitForMining(baritone);
        } else {
            // 默认等待策略：等待一小段时间
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        LOGGER.info("[宏执行] Baritone 命令执行完成: {}", command);
    }
    
    /**
     * 等待路径查找完成
     * 通过检查玩家位置与目标位置的距离来判断是否到达
     */
    private void waitForPathfinding(IBaritone baritone, BlockPos targetPos) {
        LOGGER.info("[宏执行] 等待路径查找完成，目标坐标: {}", targetPos);
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            LOGGER.warn("[宏执行] 玩家未初始化，无法等待路径查找");
            return;
        }
        
        int maxWaitTime = 300000; // 最大等待5分钟
        int checkInterval = 500; // 每500ms检查一次
        int waited = 0;
        BlockPos lastPos = null;
        int stableCount = 0; // 位置稳定计数
        final int TOLERANCE = 3; // 到达目标的容差（3格）
        
        while (waited < maxWaitTime && !stopped.get()) {
            try {
                BlockPos currentPos = mc.player.blockPosition();
                
                // 如果有目标坐标，检查是否到达目标
                if (targetPos != null) {
                    double distance = Math.sqrt(
                        Math.pow(currentPos.getX() - targetPos.getX(), 2) +
                        Math.pow(currentPos.getY() - targetPos.getY(), 2) +
                        Math.pow(currentPos.getZ() - targetPos.getZ(), 2)
                    );
                    
                    if (distance <= TOLERANCE) {
                        LOGGER.info("[宏执行] 已到达目标位置，距离: {} 格", String.format("%.2f", distance));
                        // 等待一小段时间确认位置稳定
                        Thread.sleep(1000);
                        if (mc.player.blockPosition().distSqr(targetPos) <= TOLERANCE * TOLERANCE) {
                            LOGGER.info("[宏执行] 路径查找已完成（已到达目标）");
                            break;
                        }
                    }
                }
                
                // 检查位置是否稳定（连续几次检查位置不变，说明已到达或停止）
                if (lastPos != null && currentPos.equals(lastPos)) {
                    stableCount++;
                    // 如果位置稳定超过2秒（4次检查），认为已到达或停止
                    if (stableCount >= 4) {
                        LOGGER.info("[宏执行] 路径查找已完成（位置已稳定）");
                        break;
                    }
                } else {
                    stableCount = 0;
                }
                
                lastPos = currentPos;
                
                Thread.sleep(checkInterval);
                waited += checkInterval;
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("[宏执行] 等待路径查找时被中断");
                break;
            } catch (Exception e) {
                LOGGER.error("[宏执行] 等待路径查找时出错", e);
                break;
            }
        }
        
        if (waited >= maxWaitTime) {
            LOGGER.warn("[宏执行] 等待路径查找超时");
        } else {
            LOGGER.info("[宏执行] 路径查找等待完成，耗时: {}ms", waited);
        }
    }
    
    /**
     * 等待挖矿完成
     */
    private void waitForMining(IBaritone baritone) {
        LOGGER.info("[宏执行] 等待挖矿完成...");
        
        // 挖矿通常需要手动停止或挖完目标，这里等待一小段时间
        // 可以根据需要调整等待时间
        try {
            Thread.sleep(2000); // 等待2秒，让挖矿开始
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 注意：挖矿命令通常不会自动完成，需要手动停止或挖完目标
        // 这里只是等待挖矿开始，实际完成需要根据具体情况判断
        LOGGER.info("[宏执行] 挖矿命令已启动（注意：挖矿可能需要手动停止）");
        
        // 挖矿命令会持续运行，直到被停止或完成
        // 命令记录会在宏停止或命令被替换时清除
    }
    
    /**
     * 执行 baritone 命令（非阻塞）
     * 直接使用 Baritone API 执行命令，而不是通过聊天消息
     */
    private void executeBaritoneCommand(String command) {
        logToChatAndLogger("[宏执行] 准备执行 Baritone 命令: " + command);
        LOGGER.info("[宏执行] 准备执行 Baritone 命令: {}", command);
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            LOGGER.warn("[宏执行] 无法执行 baritone 命令，玩家未初始化");
            return;
        }
        
        // 确保在主游戏线程中执行
        if (mc.isSameThread()) {
            // 已经在主线程，直接执行
            executeBaritoneCommandInternal(command);
        } else {
            // 不在主线程，切换到主线程执行
            final String cmd = command;
            mc.execute(() -> executeBaritoneCommandInternal(cmd));
            
            // 等待一小段时间确保命令被提交
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 内部方法：实际执行 Baritone 命令
     */
    private void executeBaritoneCommandInternal(String command) {
        try {
            // 获取 Baritone 实例
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone == null) {
                LOGGER.warn("[宏执行] Baritone 未加载，无法执行命令");
                return;
            }
            
            LOGGER.info("[宏执行] 已获取 Baritone 实例，准备执行命令");
            
            // 使用 Baritone 的命令管理器直接执行命令
            String fullCommand = command.trim();
            if (fullCommand.isEmpty()) {
                LOGGER.warn("[宏执行] 命令为空");
                return;
            }
            
            LOGGER.info("[宏执行] 执行 Baritone 命令: {}", fullCommand);
            
            // 执行命令
            baritone.getCommandManager().execute(fullCommand);
            
            logToChatAndLogger("[宏执行] ✓ 已通过 Baritone API 执行命令: " + fullCommand);
            LOGGER.info("[宏执行] ✓ 已通过 Baritone API 执行命令: {}", fullCommand);
            
        } catch (Exception e) {
            LOGGER.error("[宏执行] 执行 Baritone 命令时出错: {}", command, e);
            e.printStackTrace();
        }
    }
    
    /**
     * 游戏刻更新
     */
    public void onTick() {
        // 可以在这里检查时间条件等
    }
    
    /**
     * 执行 wait 命令
     * wait: 一直阻塞直到宏结束
     * wait xs: 阻塞 x 秒
     * wait xm: 阻塞 x 分钟
     * wait xh: 阻塞 x 小时
     */
    public void executeWaitCommand(String content) {
        if (stopped.get()) {
            return;
        }
        
        try {
            content = content.trim();
            
            // wait（无参数）：一直阻塞直到宏结束
            if (content.isEmpty()) {
                logToChatAndLogger("[宏执行] 执行 wait 命令，将一直阻塞直到宏结束");
                LOGGER.info("[宏执行] 执行 wait 命令，将一直阻塞直到宏结束");
                
                // 循环检查是否停止，每100ms检查一次
                while (!stopped.get()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        LOGGER.warn("[宏执行] wait 命令被中断");
                        break;
                    }
                }
                
                logToChatAndLogger("[宏执行] wait 命令结束（宏已停止）");
                LOGGER.info("[宏执行] wait 命令结束（宏已停止）");
                return;
            }
            
            // 解析时间参数
            long waitTimeMs = 0;
            
            // 匹配格式：数字 + s/m/h
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(\\d+)([smh])$");
            java.util.regex.Matcher matcher = pattern.matcher(content);
            
            if (matcher.matches()) {
                long value = Long.parseLong(matcher.group(1));
                String unit = matcher.group(2);
                
                switch (unit) {
                    case "s":
                        waitTimeMs = value * 1000; // 秒
                        break;
                    case "m":
                        waitTimeMs = value * 60 * 1000; // 分钟
                        break;
                    case "h":
                        waitTimeMs = value * 60 * 60 * 1000; // 小时
                        break;
                    default:
                        LOGGER.warn("[宏执行] wait 命令格式错误: {}", content);
                        logToChatAndLogger("[宏执行] wait 命令格式错误: " + content);
                        return;
                }
                
                logToChatAndLogger(String.format("[宏执行] 执行 wait 命令，将阻塞 %d%s", value, unit));
                LOGGER.info("[宏执行] 执行 wait 命令，将阻塞 {}ms ({} {})", waitTimeMs, value, unit);
                
                // 阻塞指定时间，但每100ms检查一次是否停止
                long startTime = System.currentTimeMillis();
                while (!stopped.get() && (System.currentTimeMillis() - startTime) < waitTimeMs) {
                    try {
                        long remaining = waitTimeMs - (System.currentTimeMillis() - startTime);
                        Thread.sleep(Math.min(100, remaining));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        LOGGER.warn("[宏执行] wait 命令被中断");
                        break;
                    }
                }
                
                if (stopped.get()) {
                    logToChatAndLogger("[宏执行] wait 命令提前结束（宏已停止）");
                    LOGGER.info("[宏执行] wait 命令提前结束（宏已停止）");
                } else {
                    logToChatAndLogger("[宏执行] wait 命令完成");
                    LOGGER.info("[宏执行] wait 命令完成");
                }
            } else {
                LOGGER.warn("[宏执行] wait 命令格式错误: {}，正确格式: wait、wait xs、wait xm、wait xh", content);
                logToChatAndLogger("[宏执行] wait 命令格式错误: " + content + "，正确格式: wait、wait xs、wait xm、wait xh");
            }
            
        } catch (Exception e) {
            LOGGER.error("[宏执行] 执行 wait 命令时出错", e);
            logToChatAndLogger("[宏执行] 执行 wait 命令时出错: " + e.getMessage());
        }
    }
    
    /**
     * 执行 check 命令
     * 语法1: check me have (item = Pickaxe,type = diamond,quantity = 1), do #goto 0 0 0;
     * 语法2: check me at = (0,0,0),do end;
     * 语法3: check time = 11000,do /home;
     */
    public void executeCheckCommand(CheckCommand cmd) {
        if (stopped.get()) {
            return;
        }
        
        try {
            boolean condition = false;
            
            switch (cmd.type) {
                case ITEM:
                    condition = checkItem(cmd);
                    break;
                case NOTHAVE:
                    condition = checkNotHaveItem(cmd);
                    break;
                case POSITION:
                    condition = checkPosition(cmd);
                    break;
                case TIME:
                    condition = checkTime(cmd);
                    break;
            }
            
            if (condition) {
                // 条件满足，执行动作
                logToChatAndLogger(String.format("[宏执行] check 条件满足，执行动作: %s", cmd.action));
                LOGGER.info("[宏执行] check 条件满足，执行动作: {}", cmd.action);
                
                // 执行动作（可能是 do 命令或其他）
                String actionToExecute = cmd.action;
                if (actionToExecute.startsWith("do ")) {
                    actionToExecute = actionToExecute.substring(3).trim();
                }
                
                // 检查是否是 end 命令（停止整个宏）
                if (actionToExecute.equals("end")) {
                    logToChatAndLogger("[宏执行] check 条件满足，执行 end 命令，停止整个宏");
                    LOGGER.info("[宏执行] check 条件满足，执行 end 命令，停止整个宏");
                    stop();
                    return;
                }
                
                // 执行其他动作
                executeDoCommand(actionToExecute);
            } else {
                // 条件不满足，继续执行下一个命令
                logToChatAndLogger("[宏执行] check 条件不满足，继续执行下一个命令");
                LOGGER.info("[宏执行] check 条件不满足，继续执行下一个命令");
            }
            
        } catch (Exception e) {
            LOGGER.error("[宏执行] 执行 check 命令时出错", e);
            logToChatAndLogger("[宏执行] 执行 check 命令时出错: " + e.getMessage());
        }
    }
    
    /**
     * 执行 run 命令
     * 语法: run name = "回家"
     * 立即启动指定的宏
     */
    public void executeRunCommand(String macroName) {
        if (stopped.get()) {
            return;
        }
        
        try {
            logToChatAndLogger(String.format("[宏执行] 执行 run 命令，启动宏: %s", macroName));
            LOGGER.info("[宏执行] 执行 run 命令，启动宏: {}", macroName);
            
            // 启动宏（独立执行，不阻塞当前宏）
            BaritoneTaskManager.getInstance().startMacro(macroName);
            
            logToChatAndLogger(String.format("[宏执行] run 命令执行成功，已启动宏: %s", macroName));
            LOGGER.info("[宏执行] run 命令执行成功，已启动宏: {}", macroName);
            
        } catch (NotFanMacroFound e) {
            LOGGER.error("[宏执行] run 命令失败: {}", e.getMessage());
            logToChatAndLogger("[宏执行] run 命令失败: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.error("[宏执行] 执行 run 命令时出错", e);
            logToChatAndLogger("[宏执行] 执行 run 命令时出错: " + e.getMessage());
        }
    }
    
    /**
     * 检查物品
     */
    private boolean checkItem(CheckCommand cmd) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) {
                return false;
            }
            
            // 获取玩家背包
            net.minecraft.world.entity.player.Inventory inventory = mc.player.getInventory();
            if (inventory == null) {
                return false;
            }
            
            int totalCount = 0;
            String itemNameLower = cmd.itemName.toLowerCase();
            
            // 判断是否是工具检查（指定了 type 参数）
            boolean isToolCheck = cmd.itemType != null && !cmd.itemType.isEmpty();
            
            // 遍历所有物品槽位（包括主手、副手、背包）
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty()) {
                    continue;
                }
                
                net.minecraft.world.item.Item item = stack.getItem();
                String itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item).toString();
                String itemName = itemId.substring(itemId.indexOf(':') + 1); // 去掉命名空间
                String itemNameLowerActual = itemName.toLowerCase();
                
                boolean nameMatches = false;
                
                if (isToolCheck) {
                    // 工具检查：支持部分匹配（如 "pickaxe" 匹配 "diamond_pickaxe"）
                    nameMatches = itemNameLowerActual.equals(itemNameLower) || 
                                 itemNameLowerActual.contains(itemNameLower) ||
                                 itemNameLower.contains(itemNameLowerActual);
                } else {
                    // 非工具检查：使用精确匹配（如 "diamond" 只匹配 "diamond"，不匹配 "diamond_pickaxe"）
                    nameMatches = itemNameLowerActual.equals(itemNameLower);
                }
                
                if (nameMatches) {
                    // 如果指定了 type，检查物品类型
                    if (isToolCheck) {
                        // 检查是否是工具类物品
                        boolean isTool = isToolItem(item);
                        if (!isTool) {
                            // 不是工具类物品，但指定了 type，报错
                            throw new IllegalArgumentException("物品类型错误: " + itemName + " 不是工具类物品，不能指定 type");
                        }
                        
                        // 检查工具类型（材质）
                        String toolMaterial = getToolMaterial(item, itemName);
                        if (toolMaterial == null || !toolMaterial.equalsIgnoreCase(cmd.itemType)) {
                            continue; // 类型不匹配，跳过
                        }
                    } else {
                        // 非工具检查：确保不是工具类物品（避免 "diamond" 匹配到 "diamond_pickaxe"）
                        boolean isTool = isToolItem(item);
                        if (isTool) {
                            // 是工具类物品，但检查的是非工具物品，跳过
                            continue;
                        }
                    }
                    
                    totalCount += stack.getCount();
                }
            }
            
            boolean result = totalCount >= cmd.quantity;
            logToChatAndLogger(String.format("[宏执行] 物品检查: 需要 %s (type=%s, quantity=%d), 找到数量=%d, 结果=%s", 
                cmd.itemName, cmd.itemType, cmd.quantity, totalCount, result));
            LOGGER.info("[宏执行] 物品检查: 需要 {} (type={}, quantity={}), 找到数量={}, 结果={}", 
                cmd.itemName, cmd.itemType, cmd.quantity, totalCount, result);
            
            return result;
            
        } catch (IllegalArgumentException e) {
            LOGGER.error("[宏执行] 物品检查错误: {}", e.getMessage());
            logToChatAndLogger("[宏执行] 物品检查错误: " + e.getMessage());
            return false;
        } catch (Exception e) {
            LOGGER.error("[宏执行] 检查物品时出错", e);
            return false;
        }
    }
    
    /**
     * 检查是否是工具类物品
     */
    private boolean isToolItem(net.minecraft.world.item.Item item) {
        try {
            // 检查是否是工具类物品（镐、斧、铲、锄、剑等）
            // 在 Minecraft 1.20.1 中，工具类物品通常继承自 DiggerItem 或 SwordItem
            if (item instanceof net.minecraft.world.item.DiggerItem || 
                item instanceof net.minecraft.world.item.SwordItem) {
                return true;
            }
            
            // 也可以通过物品名称判断
            String itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item).toString();
            String itemName = itemId.substring(itemId.indexOf(':') + 1).toLowerCase();
            
            // 工具类物品通常包含这些关键词
            String[] toolKeywords = {"pickaxe", "axe", "shovel", "hoe", "sword"};
            for (String keyword : toolKeywords) {
                if (itemName.contains(keyword)) {
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取工具材质
     */
    private String getToolMaterial(net.minecraft.world.item.Item item, String itemName) {
        try {
            // 常见的工具材质（按优先级）
            String[] materials = {"netherite", "diamond", "golden", "gold", "iron", "stone", "wooden", "wood"};
            
            for (String material : materials) {
                if (itemName.toLowerCase().contains(material)) {
                    // 标准化材质名称
                    if (material.equals("gold")) {
                        return "golden";
                    } else if (material.equals("wood")) {
                        return "wooden";
                    }
                    return material;
                }
            }
            
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 检查没有物品（nothave）
     * nothave 只允许 item 参数，不支持 type 和 quantity
     * 使用精确匹配，避免 "diamond" 匹配到 "diamond_pickaxe"
     */
    private boolean checkNotHaveItem(CheckCommand cmd) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) {
                return true; // 无法检查，假设没有物品
            }
            
            // 获取玩家背包
            net.minecraft.world.entity.player.Inventory inventory = mc.player.getInventory();
            if (inventory == null) {
                return true; // 无法检查，假设没有物品
            }
            
            String itemNameLower = cmd.itemName.toLowerCase();
            
            // 遍历所有物品槽位（包括主手、副手、背包）
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty()) {
                    continue;
                }
                
                net.minecraft.world.item.Item item = stack.getItem();
                String itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item).toString();
                String itemName = itemId.substring(itemId.indexOf(':') + 1); // 去掉命名空间
                String itemNameLowerActual = itemName.toLowerCase();
                
                // nothave 使用精确匹配，避免 "diamond" 匹配到 "diamond_pickaxe"
                // 例如：检查 "diamond" 时，只匹配 "diamond"，不匹配 "diamond_pickaxe"
                boolean nameMatches = itemNameLowerActual.equals(itemNameLower);
                
                if (nameMatches) {
                    // 找到了该物品，返回 false（有物品）
                    logToChatAndLogger(String.format("[宏执行] nothave 检查: 需要没有 %s, 但找到了 %s (数量=%d), 结果=false", 
                        cmd.itemName, itemName, stack.getCount()));
                    LOGGER.info("[宏执行] nothave 检查: 需要没有 {}, 但找到了 {} (数量={}), 结果=false", 
                        cmd.itemName, itemName, stack.getCount());
                    return false;
                }
            }
            
            // 没有找到该物品，返回 true（没有物品）
            logToChatAndLogger(String.format("[宏执行] nothave 检查: 需要没有 %s, 背包中没有, 结果=true", cmd.itemName));
            LOGGER.info("[宏执行] nothave 检查: 需要没有 {}, 背包中没有, 结果=true", cmd.itemName);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("[宏执行] 检查 nothave 时出错", e);
            return true; // 出错时假设没有物品
        }
    }
    
    /**
     * 检查位置
     */
    private boolean checkPosition(CheckCommand cmd) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) {
                return false;
            }
            
            net.minecraft.core.BlockPos pos = mc.player.blockPosition();
            int tolerance = 2; // ±2格容差
            
            boolean result = Math.abs(pos.getX() - cmd.x) <= tolerance &&
                           Math.abs(pos.getY() - cmd.y) <= tolerance &&
                           Math.abs(pos.getZ() - cmd.z) <= tolerance;
            
            logToChatAndLogger(String.format("[宏执行] 位置检查: 目标=(%d,%d,%d), 当前位置=(%d,%d,%d), 容差=%d, 结果=%s", 
                cmd.x, cmd.y, cmd.z, pos.getX(), pos.getY(), pos.getZ(), tolerance, result));
            LOGGER.info("[宏执行] 位置检查: 目标=({},{},{}), 当前位置=({},{},{}), 容差={}, 结果={}", 
                cmd.x, cmd.y, cmd.z, pos.getX(), pos.getY(), pos.getZ(), tolerance, result);
            
            return result;
            
        } catch (Exception e) {
            LOGGER.error("[宏执行] 检查位置时出错", e);
            return false;
        }
    }
    
    /**
     * 检查时间
     */
    private boolean checkTime(CheckCommand cmd) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) {
                return false;
            }
            
            long currentTime = BaritoneTaskManager.getCurrentTime();
            int timeTolerance = 50; // ±50 ticks tolerance (approx 2.5 seconds)
            
            boolean result = Math.abs(currentTime - cmd.time) <= timeTolerance;
            
            logToChatAndLogger(String.format("[宏执行] 时间检查: 目标=%d, 当前时间=%d, 容差=%d, 结果=%s", 
                cmd.time, currentTime, timeTolerance, result));
            LOGGER.info("[宏执行] 时间检查: 目标={}, 当前时间={}, 容差={}, 结果={}", 
                cmd.time, currentTime, timeTolerance, result);
            
            return result;
            
        } catch (Exception e) {
            LOGGER.error("[宏执行] 检查时间时出错", e);
            return false;
        }
    }
    
    /**
     * 停止执行
     */
    public void stop() {
        stopped.set(true);
        // 清除命令记录
        BaritoneTaskManager.getInstance().clearMacroCommand(macroName);
    }
    
    /**
     * 检查是否已停止
     */
    public boolean isStopped() {
        return stopped.get();
    }
}

