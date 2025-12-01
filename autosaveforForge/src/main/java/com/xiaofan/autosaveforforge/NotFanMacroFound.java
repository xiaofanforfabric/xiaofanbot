package com.xiaofan.autosaveforforge;

/**
 * 宏未找到异常
 * 当尝试启动不存在的宏时抛出
 */
public class NotFanMacroFound extends Exception {
    public NotFanMacroFound(String macroName) {
        super("宏未找到: " + macroName);
    }
}

