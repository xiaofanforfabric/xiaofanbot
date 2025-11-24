# KOOKBot 模块

基于 [simbot-component-kook](https://github.com/simple-robot/simbot-component-kook) SDK 开发的 KOOK（开黑啦）机器人。

## 环境要求

- Java 21+
- Gradle 8.5+

## 快速开始

### 1. 配置依赖

项目使用 Gradle 构建，依赖配置在 `build.gradle.kts` 中。

**注意**：simbot-component-kook 的 Maven 依赖坐标可能需要根据实际发布情况调整。如果构建时找不到依赖，请：

1. 查看 [simbot-component-kook 的文档](https://simbot.forte.love/component-kook.html)
2. 检查 Maven 仓库中的实际坐标
3. 或直接从源码构建并安装到本地仓库

### 2. 配置文件

复制配置文件模板：

```bash
cp src/main/resources/config.properties.example config.properties
```

编辑 `config.properties`，填入你的 KOOK Bot Token：

```properties
kook.bot.token=你的Bot_Token
```

### 3. 获取 KOOK Bot Token

1. 访问 [KOOK 开发者平台](https://developer.kookapp.cn/)
2. 创建机器人应用
3. 获取 Bot Token

### 4. 构建项目

```bash
# Windows
gradlew.bat build

# Linux/Mac
./gradlew build
```

### 5. 运行

```bash
# Windows
gradlew.bat run

# Linux/Mac
./gradlew run
```

或运行生成的 JAR：

```bash
java -jar build/libs/kookbot-1.0-SNAPSHOT.jar
```

## 项目结构

```
KOOKBot/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/xiaofan/kookbot/
│   │   │       └── KookBotMain.java  # 主程序入口
│   │   └── resources/
│   │       └── config.properties.example  # 配置模板
│   └── test/
├── build.gradle.kts  # Gradle 构建配置
├── settings.gradle.kts
└── gradle.properties
```

## 参考文档

- [simbot-component-kook 文档](https://simbot.forte.love/component-kook.html)
- [Simple Robot 应用手册](https://simbot.forte.love/)
- [KOOK 开发者文档](https://developer.kookapp.cn/doc/)

## 注意事项

1. **依赖坐标**：如果构建失败，可能需要调整 `build.gradle.kts` 中的依赖坐标
2. **Java 版本**：确保使用 Java 21 或更高版本
3. **配置文件**：`config.properties` 包含敏感信息，已添加到 `.gitignore`

