# KOOKBot 环境配置指南

## 当前状态

✅ 已完成：
- Gradle 项目配置
- 基础代码结构
- 配置文件模板

❌ 待解决：
- **依赖无法下载**：`simbot-component-kook` 可能不在 Maven Central

## 解决方案

### 方案1：从源码构建并安装到本地仓库（推荐）

如果依赖不在 Maven Central，需要从源码构建：

```bash
# 1. 克隆 simbot-component-kook 仓库
git clone https://github.com/simple-robot/simbot-component-kook.git
cd simbot-component-kook

# 2. 构建并安装到本地 Maven 仓库
./gradlew publishToMavenLocal
# Windows: gradlew.bat publishToMavenLocal

# 3. 回到 KOOKBot 项目，更新依赖版本为本地安装的版本
```

然后更新 `build.gradle.kts` 中的版本号。

### 方案2：确认正确的 Maven 坐标和版本

请提供以下信息之一：

1. **正确的依赖坐标**：
   - 从 [simbot-component-kook 文档](https://simbot.forte.love/component-kook.html) 获取
   - 或从项目的 `build.gradle.kts` 查看实际发布的坐标

2. **Maven 仓库地址**：
   - 如果不在 Maven Central，需要添加正确的仓库地址

3. **版本号**：
   - 当前尝试：`4.14.0`
   - 可能需要：`4.1.4`, `4.1.1`, `4.0.0-beta4` 等

### 方案3：使用已发布的版本

如果你知道 simbot-component-kook 已经发布到某个 Maven 仓库，请提供：
- 仓库 URL
- 正确的 groupId:artifactId:version

## 下一步操作

请选择以下方式之一：

### 选项A：从源码构建（如果你有权限）
```bash
git clone https://github.com/simple-robot/simbot-component-kook.git
cd simbot-component-kook
./gradlew publishToMavenLocal
```

### 选项B：提供正确的依赖信息
请提供：
- 正确的 Maven 坐标（groupId:artifactId:version）
- 或 Maven 仓库地址

### 选项C：检查官方文档
访问以下链接，找到正确的依赖配置：
- https://simbot.forte.love/component-kook.html
- https://github.com/simple-robot/simbot-component-kook

## 配置 KOOK Bot

一旦依赖问题解决，还需要：

1. **获取 KOOK Bot Token**：
   - 访问 [KOOK 开发者平台](https://developer.kookapp.cn/)
   - 创建机器人应用
   - 获取 Client ID 和 WebSocket Token

2. **创建配置文件**：
   ```bash
   cp src/main/resources/config.properties.example config.properties
   ```
   编辑 `config.properties`，填入 Token

3. **创建 Bot 配置文件**：
   在 `src/main/resources/simbot-bots/` 目录创建 `kook.bot.json`：
   ```json
   {
     "component": "simbot.kook",
     "ticket": {
       "clientId": "你的Client ID",
       "token": "你的WebSocket Token"
     }
   }
   ```

## 测试构建

依赖配置正确后，运行：

```bash
cd KOOKBot
gradlew.bat build
```

如果构建成功，说明环境配置完成！

