package com.xiaofan.qqbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NapCat QQ机器人主程序入口
 * 
 * 功能：
 * 1. 通过WebSocket实时接收群消息事件
 * 2. 监听并记录群聊消息
 * 3. 检测到"测试机器人"消息时自动回复"机器人在线"
 */

/**
 *                             _ooOoo_
 *                            o8888888o
 *                            88" . "88
 *                            (| -_- |)
 *                            O\  =  /O
 *                         ____/`---'\____
 *                       .'  \\|     |//  `.
 *                      /  \\|||  :  |||//  \
 *                     /  _||||| -:- |||||-  \
 *                     |   | \\\  -  /// |   |
 *                     | \_|  ''\---/''  |   |
 *                     \  .-\__  `-`  ___/-. /
 *                   ___`. .'  /--.--\  `. . __
 *                ."" '<  `.___\_<|>_/___.'  >'"".
 *               | | :  `- \`.;`\ _ /`;.`/ - ` : | |
 *               \  \ `-.   \_ __\ /__ _/   .-` /  /
 *          ======`-.____`-.___\_____/___.-`____.-'======
 *                             `=---='
 *          ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
 *                     佛祖保佑        永无BUG
 *            佛曰:
 *                   写字楼里写字间，写字间里程序员；
 *                   程序人员写程序，又拿程序换酒钱。
 *                   酒醒只在网上坐，酒醉还来网下眠；
 *                   酒醉酒醒日复日，网上网下年复年。
 *                   但愿老死电脑间，不愿鞠躬老板前；
 *                   奔驰宝马贵者趣，公交自行程序员。
 *                   别人笑我忒疯癫，我笑自己命太贱；
 *                   不见满街漂亮妹，哪个归得程序员？
*/

public class botmain {
    private static Logger logger;
    private static QQBot bot;
    
    public static void main(String[] args) {
        // 配置日志（必须在任何Logger使用之前）
        LogConfig.configure();
        
        // 初始化Logger（必须在LogConfig.configure()之后）
        logger = LoggerFactory.getLogger(botmain.class);
        
        logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        logger.info("NapCat QQ机器人服务启动...");
        
        // 验证配置
        ConfigManager.validateConfig();
        
        logger.info("NapCat WebSocket地址: {}", QQBot.NAPCAT_WS_URL);
        logger.info("NapCat API地址: {}", QQBot.NAPCAT_API_URL);
        logger.info("监听所有群消息");
        logger.info("触发词: '{}' -> 回复: '{}'", QQBot.TRIGGER_MESSAGE, QQBot.REPLY_MESSAGE);
        logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        // 初始化并启动机器人
        bot = new QQBot();
        bot.start();
        
        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("正在关闭服务...");
            if (bot != null) {
                bot.stop();
            }
            logger.info("服务已关闭");
        }));
        
        // 主线程保持运行
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            logger.error("主线程被中断", e);
            Thread.currentThread().interrupt();
        }

    }
}
