# xiaofanbot - QQ机器人 & Minecraft客户端模组

[![GitHub](https://img.shields.io/badge/GitHub-xiaofanforfabric%2Fxiaofanbot-blue)](https://github.com/xiaofanforfabric/xiaofanbot)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE.txt)

一个功能丰富的QQ机器人系统，集成Minecraft服务器消息同步、AI对话、签到系统等功能。

## 📋 项目概述

本项目包含三个主要模块：

1. **QQbot模块** - 基于NapCat的QQ机器人，提供多种群聊和私聊功能
2. **Fabric客户端模组** - Minecraft 1.21.5客户端模组，提供服务器信息API和消息监听
3. **autosaveforForge模块** - Minecraft 1.20.1 Forge客户端模组，提供**革命性的自动化宏代码执行系统** 🚀

## 🏗️ 项目结构

```
autosave/
├── QQbot/                    # QQ机器人模块
│   ├── src/main/java/com/xiaofan/qqbot/
│   │   ├── botmain.java              # 主程序入口
│   │   ├── QQBot.java                # 核心机器人类
│   │   ├── BanListManager.java       # 黑名单管理
│   │   ├── DatabaseManager.java      # 数据库管理（MySQL）
│   │   ├── CheckInHandler.java       # 签到处理器
│   │   ├── PointsQueryHandler.java   # 积分查询处理器
│   │   ├── TipSubmissionHandler.java # 投稿处理器
│   │   ├── TipHandler.java           # Tip查询处理器
│   │   ├── HelpHandler.java          # 帮助菜单处理器
│   │   ├── CatgirlAIService.java     # 猫娘AI服务（Cloudflare Workers AI）
│   │   ├── CatgirlHandler.java       # 猫娘AI消息处理器
│   │   ├── PlayerCountQueryHandler.java # 人数查询处理器
│   │   ├── ServerCommandHandler.java # 服务器命令转发
│   │   ├── ServerMessageMonitor.java # 服务器消息监控
│   │   └── LogConfig.java            # 日志配置
│   └── build.gradle
│
├── src/                      # Fabric客户端模组
│   ├── client/java/com/xiaofan/world/autosave/
│   │   ├── message/mes.java         # 服务器消息API（端口2000）
│   │   ├── ClientControlServer.java # 客户端控制（端口8083）
│   │   └── ...                       # 其他客户端功能
│   └── main/
│
├── autosaveforForge/         # Forge客户端模组（V3.0新增）
│   ├── src/main/java/com/xiaofan/autosaveforforge/
│   │   ├── MacroExecutor.java       # 宏执行器（核心）
│   │   ├── MacroParser.java         # 宏解析器（核心）
│   │   ├── BaritoneTaskManager.java # 任务管理器（核心）
│   │   ├── MacroWebServer.java      # 宏管理Web服务器（端口8079）
│   │   ├── StatusHttpServer.java    # 状态服务器（端口8083）
│   │   ├── ChatMonitorServer.java   # 聊天监控（端口8081）
│   │   ├── ServerInfoAPI.java       # 服务器信息API（端口2000）
│   │   ├── DoCommandHandler.java    # /do命令处理器
│   │   └── ...                       # 其他功能模块
│   └── FanMacrodoc.md               # 宏语言完整文档
│
└── SimpfunPassAPI/           # API服务模块
```

## ✨ 功能特性

### 🤖 QQ机器人功能

#### 基础功能
- **触发词回复**：`oi` → 回复 `io`
- **帮助菜单**：发送 `帮助` 查看所有可用命令

#### 服务器查询
- **人数查询**：发送 `人数查询` 查询Minecraft服务器在线人数
- **服务器消息同步**：自动将Minecraft服务器聊天消息同步到指定QQ群

#### 签到系统
- **签到**：发送 `签到` 每日签到获得积分（24小时冷却）
- **积分查询**：发送 `查询积分` 查看当前积分
- **数据库**：使用MySQL存储用户签到数据（`qddata.userdata`表）

#### 游戏ID绑定
- **绑定**：发送 `绑定` 绑定游戏ID到QQ号
- **流程**：
  1. 用户发送"绑定"触发绑定流程
  2. 系统生成6位验证码
  3. 用户在Minecraft服务器内发送 `/c 绑定（验证码）` 完成绑定
  4. 验证码2分钟有效，超时需重新绑定
- **API**：端口2001提供 `/pass_code` API接收验证码
- **限制**：同时只能有一个用户进行绑定

#### 消息屏蔽
- **屏蔽**：发送 `/mute` 屏蔽自己的游戏消息
- **功能**：将用户的游戏ID添加到屏蔽列表，服务器消息同步时自动过滤该玩家的消息
- **要求**：必须先完成游戏ID绑定
- **文件**：`mute.txt`（位于JAR包同目录）

#### 投稿系统
- **投稿**：发送 `投稿 （内容）` 投稿内容到数据库
- **Tip查询**：发送 `tip` 随机获取一条投稿内容
- **数据库**：使用MySQL存储投稿数据（`qddata.tipdata`表）

#### AI对话（猫娘）
- **触发方式**：
  - 群聊：消息开头 `@写了亿小时bug` 或 `@wans2024` + 问题
  - 私聊：直接发送消息
- **AI服务**：Cloudflare Workers AI（`@cf/meta/llama-3.1-8b-instruct`）
- **频率限制**：每分钟10次，超过提示"调用过于频繁，请一分钟后再试。"
- **角色设定**：猫娘角色，每句话结尾带"喵"

#### 服务器命令转发
- **触发方式**：在指定群组发送 `/c （内容）`
- **目标群组**：可在代码中配置（默认支持多个群组）
- **功能**：将消息转发到Minecraft服务器

#### 安全功能
- **黑名单管理**：支持黑名单用户，黑名单用户无法使用任何功能
- **黑名单文件**：`ban.txt`（位于JAR包同目录）

### 🎮 Minecraft客户端模组功能

#### 服务器信息API（端口2000）
- **`/need_server_info`**：获取服务器在线人数
- **`/get_server_last_message`**：获取最后一条玩家聊天消息（队列模式）
- **`/send_message_to_server`**：发送命令到Minecraft服务器

#### 客户端控制（端口8083）
- Web界面控制客户端功能

### 🎯 autosaveforForge模块功能（V3.0 革命性更新！）

#### ⭐ 自动化宏代码执行系统（核心功能）

**这是本项目的革命性创新！** 通过自定义宏语言，你可以编写简单的文本文件来自动执行复杂的游戏任务，无需编程知识！

##### 🌟 核心特性

- **📝 自定义宏语言**：简单易学的文本语法，像写脚本一样编写自动化任务
- **🔄 条件执行**：支持位置检查、时间检查、物品检查等智能条件判断
- **⚙️ 函数系统**：支持函数定义和调用，代码复用更简单
- **🎮 Baritone集成**：无缝集成Baritone路径查找，自动挖矿、探索、移动
- **🌐 Web管理界面**：通过浏览器管理宏，启动/停止/查看状态
- **📁 文件监听**：修改宏文件后自动重载，无需重启游戏
- **🛡️ 冲突检测**：智能检测命令冲突，自动停止冲突的宏
- **⏰ 时间控制**：支持基于游戏时间的定时任务

##### 📚 宏语言文档

完整的宏语言语法和使用说明请查看：[**FanMacrodoc.md**](autosaveforForge/FanMacrodoc.md)

##### 🎬 示例视频

> 📹 **演示视频**：[点击观看自动化宏系统演示](https://web.xiaofansmp.cc/video/VID_20251201_021329.mp4)

##### 💡 示例代码

以下是在演示视频中使用的三个宏文件：

**1. 回家宏** (`回家.txt`)
```fanmacro
fan_main:
    do fun "sleep"

fun name="sleep";
    if time >= 11000
        do #stop;
        do #goto -23 77 -3073
        do #set allowBreak false;
        do #set allowPlace false;
        do #goto 0 85 -3062;
        do /home;
        do #set allowBreak true;
        do #set allowPlace true;
        do end;
    else
        do wait 1m;
        do fun "sleep";
```

**2. 挖矿宏** (`挖矿.txt`)
```fanmacro
fan_main:
    do fun "goto"

fun name="goto";
    if me at = (0,85,-3062)
        do #set allowBreak false;
        do #set allowPlace false;
        do #goto 19 78 -3016;
        do #set allowBreak true;
        do #set allowPlace true;
        check me nothave (item = coal), do #mine minecraft:coal_ore;
        check me nothave (item = raw_iron), do #mine minecraft:iron_ore;
        check me nothave (item = diamond), do #mine minecraft:diamond_ore minecraft:deepslate_diamond_ore;
        do wait 5m;
        do #stop;
        run name = "立即回家"
        do end;
    else
        do #stop;
        do /home;
        do fun "goto";
```

**3. 立即回家宏** (`立即回家.txt`)
```fanmacro
fan_main:
    do #stop;
    do #goto -23 77 -3073
    do #set allowBreak false;
    do #set allowPlace false;
    do #goto 0 85 -3062;
    do /home;
    do #set allowBreak true;
    do #set allowPlace true;
    do end;
```

##### 🚀 快速开始

1. **安装模组**：将模组放入 `.minecraft/mods/` 目录
2. **创建宏文件**：在 `.minecraft/config/do/` 目录创建 `.txt` 文件
3. **编写宏代码**：使用宏语言编写自动化任务
4. **启动宏**：
   - 游戏内：使用 `/do <宏名>` 命令
   - Web界面：访问 `http://localhost:8079`

#### 其他功能

- **状态监控服务器**（端口8083）：查看游戏状态，控制服务器连接
- **聊天监控服务器**（端口8081）：实时监控游戏聊天消息
- **服务器信息API**（端口2000）：提供服务器信息查询接口
- **自动睡眠控制**：智能控制游戏内睡眠
- **死亡处理**：玩家死亡时自动停止所有任务
- **性能优化**：禁用窗口失去焦点时暂停，保持后台运行

## 🚀 快速开始

### 环境要求

- Java 17+ (Forge模组) / Java 21+ (Fabric模组和QQbot)
- MySQL 8.0+ (仅QQbot需要)
- NapCat QQ机器人框架 (仅QQbot需要)
- Minecraft 1.20.1 + Forge 47.4.12 (autosaveforForge模组)
- Minecraft 1.21.5 + Fabric Loader 0.17.3 (Fabric模组)
- Baritone Mod (autosaveforForge模组需要)

### 配置步骤

#### 1. 数据库配置

创建MySQL数据库和表：

```sql
-- 创建数据库
CREATE DATABASE qddata;

-- 创建用户数据表
CREATE TABLE userdata (
    id INT AUTO_INCREMENT PRIMARY KEY,
    qq_id BIGINT NOT NULL UNIQUE,
    username VARCHAR(255) NOT NULL,
    qd INT NOT NULL DEFAULT 1,
    qd_last_time DATETIME,
    reg_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    game_id VARCHAR(255) DEFAULT NULL
);

-- 创建投稿数据表
CREATE TABLE tipdata (
    id INT AUTO_INCREMENT PRIMARY KEY,
    tip TEXT NOT NULL,
    reg_user VARCHAR(255) NOT NULL,
    reg_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 创建用户并授权（请替换为你的实际密码）
CREATE USER 'qddata'@'%' IDENTIFIED BY 'your_password_here';
GRANT ALL PRIVILEGES ON qddata.* TO 'qddata'@'%';
FLUSH PRIVILEGES;
```

#### 2. 配置文件设置

**创建配置文件：**

在QQbot模块的JAR包同目录（或项目根目录）创建 `config.properties` 文件：

```bash
# 从模板复制（推荐）
cp QQbot/src/main/resources/config.properties.template QQbot/config.properties

# 或从示例复制
cp QQbot/config.properties.example QQbot/config.properties
```

**填写配置信息：**

编辑 `config.properties`，填入实际值：

```properties
# NapCat配置
napcat.token=你的实际Token

# 数据库配置
db.url=jdbc:mysql://your_db_host:3306/qddata?useSSL=false&serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true
db.user=your_db_user
db.password=your_db_password

# Cloudflare AI配置
cloudflare.account.id=你的账户ID
cloudflare.api.key=你的API密钥（可选，优先使用环境变量）
```

**环境变量配置（可选）：**

Cloudflare API Key优先从环境变量读取：

```bash
# Windows PowerShell
$env:AI_API_KEY = "your_cloudflare_api_key"

# Linux/Mac
export AI_API_KEY="your_cloudflare_api_key"
```

> 📖 **详细配置说明请参考**：[QQbot/CONFIG_GUIDE.md](QQbot/CONFIG_GUIDE.md)

#### 3. NapCat配置

确保NapCat运行在以下端口：
- API端口：`3000`
- WebSocket端口：`3001`
- Token：在 `config.properties` 中配置 `napcat.token`

#### 4. 编译和运行

**编译QQbot模块：**
```bash
cd QQbot
./gradlew build
```

**运行QQbot：**
```bash
java -jar build/libs/qqbot-1.0-SNAPSHOT.jar
```

**编译Fabric模组：**
```bash
./gradlew build
```

**编译Forge模组：**
```bash
cd autosaveforForge
./gradlew build
```

编译后的模组文件位于：`autosaveforForge/build/libs/autosaveforforge-1.0-SNAPSHOT.jar`

## 📖 使用说明

### QQ机器人命令列表

| 命令 | 功能 | 示例 |
|------|------|------|
| `oi` | 基础回复 | `oi` → `io` |
| `人数查询` | 查询服务器在线人数 | `人数查询` |
| `签到` | 每日签到 | `签到` |
| `查询积分` | 查询当前积分 | `查询积分` |
| `绑定` | 绑定游戏ID到QQ号 | `绑定` |
| `/mute` | 屏蔽自己的游戏消息 | `/mute` |
| `投稿 （内容）` | 投稿内容 | `投稿 这是一个小贴士` |
| `tip` | 随机获取投稿 | `tip` |
| `@写了亿小时bug （问题）` | AI对话（群聊） | `@写了亿小时bug 你好吗？` |
| `@wans2024 （问题）` | AI对话（群聊） | `@wans2024 今天天气怎么样？` |
| 私聊消息 | AI对话（私聊） | 直接发送消息 |
| `/c （内容）` | 转发到服务器 | `/c 大家好` |
| `帮助` | 显示帮助菜单 | `帮助` |

### 端口使用

| 端口 | 模块 | 功能 |
|------|------|------|
| 2000 | Fabric客户端 / Forge客户端 | 服务器信息API |
| 2001 | QQbot模块 | 绑定验证码API |
| 3000 | NapCat | HTTP API |
| 3001 | NapCat | WebSocket |
| 8079 | Forge客户端 | 宏管理Web界面 |
| 8081 | Forge客户端 | 聊天监控Web界面 |
| 8083 | Fabric客户端 / Forge客户端 | 客户端控制 / 状态监控 |

## 🔧 配置说明

### 配置文件说明

所有敏感配置都通过 `config.properties` 文件管理：

- **配置文件位置**：
  - 生产环境：JAR包同目录的 `config.properties`
  - 开发环境：`src/main/resources/config.properties`（不推荐，会被打包）
- **配置文件模板**：`config.properties.template`（已提交到Git）
- **配置文件示例**：`config.properties.example`（已提交到Git，包含示例值）
- **安全保护**：`config.properties` 已在 `.gitignore` 中，不会被提交到GitHub

### Cloudflare Workers AI配置

- **模型**：`@cf/meta/llama-3.1-8b-instruct`
- **账户ID**：从 `config.properties` 的 `cloudflare.account.id` 读取
- **API Key**：优先从环境变量 `AI_API_KEY` 读取，其次从配置文件读取
- **API URL**：自动构建为 `https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/run/{model}`

### 数据库配置

数据库连接信息从 `config.properties` 读取：
- **URL**：`db.url`
- **用户名**：`db.user`
- **密码**：`db.password`

### 黑名单配置

黑名单文件：`ban.txt`（位于JAR包同目录）

格式：
```
QQ号1;
QQ号2;
```

示例：
```
1234567890;
9876543210;
```

## 📁 文件说明

### QQbot模块核心文件

- `botmain.java` - 程序入口
- `QQBot.java` - 核心机器人类，处理WebSocket消息
- `BanListManager.java` - 黑名单管理
- `DatabaseManager.java` - MySQL数据库操作
- `CatgirlAIService.java` - Cloudflare Workers AI服务
- `CatgirlHandler.java` - AI消息处理（@触发和私聊）
- `CheckInHandler.java` - 签到功能
- `PointsQueryHandler.java` - 积分查询
- `TipSubmissionHandler.java` - 投稿功能
- `TipHandler.java` - Tip查询
- `HelpHandler.java` - 帮助菜单
- `PlayerCountQueryHandler.java` - 人数查询
- `ServerCommandHandler.java` - 服务器命令转发
- `ServerMessageMonitor.java` - 服务器消息监控
- `LogConfig.java` - 日志配置（输出到last.log）

### Fabric客户端模组核心文件

- `mes.java` - 服务器消息API（端口2000）
- `ClientControlServer.java` - 客户端控制（端口8083）

### autosaveforForge模块核心文件

- `MacroExecutor.java` - 宏执行器（核心，执行宏命令）
- `MacroParser.java` - 宏解析器（核心，解析宏文件）
- `BaritoneTaskManager.java` - 任务管理器（核心，管理宏生命周期）
- `MacroWebServer.java` - 宏管理Web服务器（端口8079）
- `StatusHttpServer.java` - 状态监控服务器（端口8083）
- `ChatMonitorServer.java` - 聊天监控服务器（端口8081）
- `ServerInfoAPI.java` - 服务器信息API（端口2000）
- `DoCommandHandler.java` - `/do`命令处理器
- `DeathHandler.java` - 死亡处理（自动停止任务）
- `AutoSleepController.java` - 自动睡眠控制
- `WorldTimeHUD.java` - 世界时间显示
- `PerformanceController.java` - 性能优化控制

## 🔒 安全注意事项

1. **配置文件管理**：
   - ✅ 使用 `config.properties` 存储敏感信息
   - ✅ 配置文件已在 `.gitignore` 中，不会被提交到GitHub
   - ✅ 使用 `config.properties.template` 作为模板参考
   - ❌ 不要将真实配置提交到Git
2. **API密钥管理**：优先使用环境变量存储 `AI_API_KEY`，其次使用配置文件
3. **数据库安全**：生产环境请修改数据库密码，不要使用默认密码
4. **黑名单功能**：定期检查和管理黑名单文件（`ban.txt` 也在 `.gitignore` 中）
5. **频率限制**：AI对话有频率限制，防止滥用

## 🐛 已知问题

- AI功能在显示帮助菜单时可能误触发（已修复，使用严格模式检测@消息）
- 远程main分支无法删除（GitHub默认分支保护）

## 📝 更新日志

### V3.0版本（最新）🚀
- **⭐ 革命性功能：自动化宏代码执行系统**
  - 自定义宏语言，支持函数、条件语句、循环
  - 无缝集成Baritone路径查找
  - Web管理界面（端口8079）
  - 文件监听自动重载
  - 智能命令冲突检测
  - 基于游戏时间的定时任务
- 状态监控服务器（端口8083）
- 聊天监控服务器（端口8081）
- 服务器信息API（端口2000）
- 自动睡眠控制
- 死亡自动停止任务
- 性能优化（禁用窗口失去焦点暂停）

### V1版本
- 基础触发词功能
- 签到系统
- 积分查询
- 投稿系统
- Tip查询
- 帮助菜单
- 猫娘AI对话（Cloudflare Workers AI）
- 服务器消息同步
- 服务器命令转发
- 黑名单管理

## 📄 许可证

MIT License - 详见 [LICENSE.txt](LICENSE.txt)

## 🤝 贡献

欢迎提交Issue和Pull Request！

## 📞 联系方式

- GitHub: [xiaofanforfabric/xiaofanbot](https://github.com/xiaofanforfabric/xiaofanbot)

---

**注意**：本项目为特定服务器环境开发，使用前请根据实际情况修改配置。
