

⚠️ 重要提示: 本模组是基于特定服务器环境开发的参考实现。开源代码中已移除所有硬编码的API密钥和敏感信息，建议不要直接用于生产环境，请作为技术参考使用。

📋 项目说明

这是一个为Minecraft邦国崛起服务器定制的自动化模组，展示了Fabric模组开发的各种技术实现。代码主要供学习和参考使用。

请注意：开源版本已移除所有敏感配置（如API密钥），您需要根据自己的环境进行配置。

✨ 功能演示（仅供参考）

🤖 AI聊天系统

// 开源版本中API_KEY为空，需要自行配置
private static final String API_KEY = ""; // 请从安全配置源获取


🏰 服务器特定功能

• 邦国崛起服务器忠诚度自动恢复

• 特定消息格式的自动化处理

• 服务器推广话术模板

🚀 快速开始（配置指南）

1. 获取代码



2. 配置环境变量和敏感信息

重要：开源代码不包含任何有效配置，您需要自行创建配置文件：
// 创建 config/rok-auto-helper.properties
ai.api-key=your_cloudflare_api_key_here
ai.api-url=your_cloudflare_api_url_here
server.target-phrase=忠诚度低下
server.welcome-command=c 欢迎回来
web.port=8081


3. 安全配置建议

// 推荐使用环境变量或外部配置文件
public class SecureConfig {
public static String getApiKey() {
return System.getenv("CLOUDFLARE_API_KEY");
// 或从加密的配置文件中读取
}
}


🛠️ 配置说明

必须配置的参数

参数 说明 获取方式

ai.api-key Cloudflare AI API密钥 从Cloudflare控制台创建

ai.api-url Cloudflare AI API地址 对应您的账户ID

server.* 服务器特定参数 根据目标服务器调整

配置文件示例

# config/rok-auto-helper.properties
# AI服务配置（必须自行申请）
ai.api-key=cf_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
ai.api-url=https://api.cloudflare.com/client/v4/accounts/your-account-id/ai/run/@cf/meta/llama-3-8b-instruct

# 服务器配置（根据实际情况修改）
server.trigger-phrase=忠诚度低下
server.restore-command=u restore confirm
server.announcement-message=大家好，这里是自动化助手...

# Web服务配置
web.enabled=true
web.port=8081


⚠️ 使用建议

为什么建议不要直接使用？

• 🔸 需要大量配置 - 开源版本不包含可立即运行的配置

• 🔸 特定业务逻辑 - 基于特定服务器的规则，需要适配

• 🔸 安全考虑 - 您需要自行实现安全的配置管理

推荐的使用方式

• ✅ 参考架构设计 - 学习模组的整体结构

• ✅ 借鉴技术实现 - 事件处理、网络通信等关键技术

• ✅ 安全实践 - 按照指南安全地配置敏感信息

• ✅ 自定义开发 - 基于技术思路开发适合自己需求的功能

🔒 安全注意事项

密钥管理最佳实践

1. 永远不要提交密钥到版本控制
2. 使用环境变量或外部配置文件
3. 为不同环境使用不同密钥
4. 定期轮换密钥

安全的配置示例

public class AIChatService {
// 从环境变量读取，避免硬编码
private static final String API_KEY =
System.getenv().getOrDefault("CLOUDFLARE_API_KEY", "");

    private static final String API_URL = 
        System.getenv().getOrDefault("CLOUDFLARE_API_URL", "");
        
    // 或者从外部配置文件读取
    private static final Properties config = loadConfig();
}


📖 学习价值

技术亮点

1. Fabric模组架构 - 完整的客户端模组结构
2. 事件监听机制 - 聊天消息、游戏事件处理
3. HTTP服务器集成 - 内嵌Web管理界面
4. AI API集成 - Cloudflare AI服务调用示例
5. 并发安全处理 - 原子操作和线程安全

代码结构


src/main/java/com/xiaofan/world/autosave/java/
├── AIChatListener.java      # 事件监听器示例
├── AIChatService.java       # 外部API集成示例（需配置密钥）
├── webserver.java           # 内嵌HTTP服务器示例
├── ScreenLoggerUtil.java    # GUI和HUD渲染示例
└── ...                      # 其他功能模块


📄 许可证

MIT License - 允许学习参考，建议根据实际需求进行修改和安全配置。

⚠️ 免责声明

本代码库主要作为技术参考和学习材料：
• 开源版本已移除所有敏感信息，不包含可立即运行的配置

• 使用者需自行申请和配置相关API服务

• 请遵守Minecraft EULA和相关法律法规

• 建议在测试环境中充分验证后再部署使用

💡 下一步行动

1. 申请API服务 - 获取Cloudflare AI等必要的服务权限
2. 配置环境 - 按照指南创建配置文件
3. 测试验证 - 在安全的环境中测试功能
4. 自定义开发 - 根据需求进行调整和扩展

安全第一 - 请务必妥善管理您的API密钥和敏感配置！

技术分享，共同进步！