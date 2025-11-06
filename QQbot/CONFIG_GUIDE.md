# 配置文件使用指南

## 📋 概述

本项目使用配置文件来管理敏感信息（如API Token、数据库密码等），确保这些信息不会被提交到GitHub。

## 🔧 配置步骤

### 1. 创建配置文件

在QQbot模块的JAR包同目录（或项目根目录）创建 `config.properties` 文件。

**方式一：从模板复制（推荐）**

```bash
# Windows PowerShell
Copy-Item QQbot\src\main\resources\config.properties.template QQbot\config.properties

# Linux/Mac
cp QQbot/src/main/resources/config.properties.template QQbot/config.properties
```

**方式二：手动创建**

在JAR包同目录创建 `config.properties` 文件。

### 2. 填写配置信息

编辑 `config.properties` 文件，填入实际的配置值：

```properties
# NapCat配置
napcat.api.url=http://127.0.0.1:3000
napcat.ws.url=ws://127.0.0.1:3001
napcat.token=你的实际Token

# 数据库配置
db.url=jdbc:mysql://your_db_host:3306/qddata?useSSL=false&serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true
db.user=your_db_user
db.password=your_db_password

# Cloudflare AI配置
cloudflare.account.id=你的账户ID
cloudflare.api.key=你的API密钥（可选，优先使用环境变量AI_API_KEY）
```

### 3. 配置文件位置

配置文件会按以下优先级查找：

1. **JAR包同目录**（生产环境推荐）
   - 将 `config.properties` 放在JAR包同目录
   - 例如：`/path/to/qqbot-1.0-SNAPSHOT.jar` 和 `/path/to/config.properties`

2. **Classpath**（开发环境）
   - 放在 `src/main/resources/config.properties`
   - 注意：此文件会被打包到JAR中，**不要**将真实配置放在这里

## 🔒 安全说明

### 已添加到 .gitignore

以下文件/目录已被添加到 `.gitignore`，不会被提交到Git：

- `config.properties` - 配置文件（包含敏感信息）
- `ban.txt` - 黑名单文件
- `*.log` - 日志文件
- `*.properties` - 所有properties文件（除了模板文件）

### 配置文件模板

`config.properties.template` 文件会被提交到Git，作为配置参考，但不包含真实敏感信息。

## 📝 配置项说明

### NapCat配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `napcat.api.url` | NapCat HTTP API地址 | `http://127.0.0.1:3000` |
| `napcat.ws.url` | NapCat WebSocket地址 | `ws://127.0.0.1:3001` |
| `napcat.token` | NapCat API Token | `YOUR_TOKEN_HERE` |

### 数据库配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `db.url` | MySQL连接URL | `jdbc:mysql://localhost:3306/qddata?...` |
| `db.user` | 数据库用户名 | `root` |
| `db.password` | 数据库密码 | `password` |

### Cloudflare AI配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `cloudflare.account.id` | Cloudflare账户ID | `YOUR_ACCOUNT_ID_HERE` |
| `cloudflare.api.key` | API密钥（可选） | 空（优先从环境变量`AI_API_KEY`读取） |

**注意**：`cloudflare.api.key` 优先从环境变量 `AI_API_KEY` 读取，如果环境变量未设置，才从配置文件读取。

### 机器人基础配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `bot.trigger.message` | 触发词 | `oi` |
| `bot.reply.message` | 回复消息 | `io` |

## 🚀 使用方式

### 开发环境

1. 在 `QQbot/src/main/resources/` 目录创建 `config.properties`
2. 填写配置信息
3. 运行程序时会自动加载

**注意**：开发环境的配置文件会被打包到JAR中，建议使用占位符值。

### 生产环境（推荐）

1. 编译JAR包：`./gradlew build`
2. 将JAR包和配置文件放在同一目录：
   ```
   /path/to/bot/
   ├── qqbot-1.0-SNAPSHOT.jar
   └── config.properties
   ```
3. 运行：`java -jar qqbot-1.0-SNAPSHOT.jar`

## ⚠️ 注意事项

1. **不要提交真实配置**：确保 `config.properties` 在 `.gitignore` 中
2. **环境变量优先**：`AI_API_KEY` 环境变量优先于配置文件
3. **配置文件验证**：启动时会自动验证关键配置，如果未配置会显示警告
4. **默认值**：如果配置文件不存在或配置项缺失，会使用默认值（脱敏版本）

## 🔍 验证配置

程序启动时会自动验证配置，如果关键配置未设置，会在日志中显示警告：

```
⚠️ NapCat Token未配置，请设置 config.properties 中的 napcat.token
⚠️ Cloudflare API Key未配置（环境变量AI_API_KEY或config.properties中的cloudflare.api.key）
```

## 📚 相关文件

- `ConfigManager.java` - 配置管理器实现
- `config.properties.template` - 配置文件模板
- `.gitignore` - Git忽略规则

