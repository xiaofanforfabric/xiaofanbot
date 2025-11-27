# Java 版本要求

## 当前问题

系统检测到 Java 版本为 **Java 8**，但 `simbot-component-kook` 需要 **Java 17 或更高版本**。

## 解决方案

### 方案1：安装 Java 17 或 21（推荐）

1. **下载 Java 17 或 21**：
   - Oracle JDK: https://www.oracle.com/java/technologies/downloads/
   - OpenJDK: https://adoptium.net/
   - 或使用包管理器安装

2. **配置环境变量**：
   ```bash
   # Windows
   # 设置 JAVA_HOME 指向 Java 17+ 的安装目录
   # 例如：C:\Program Files\Java\jdk-17
   
   # 添加到 PATH
   # %JAVA_HOME%\bin
   ```

3. **验证安装**：
   ```bash
   java -version
   # 应该显示 java version "17.x.x" 或更高
   ```

### 方案2：使用 Gradle Toolchain（如果已安装 Java 17+）

如果系统已安装 Java 17+ 但默认使用的是 Java 8，可以在 `build.gradle.kts` 中配置：

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
```

Gradle 会自动下载并使用指定的 Java 版本。

### 方案3：使用项目中的 Java（如果有）

如果项目根目录或其他位置有 Java 17+，可以：
1. 设置 `JAVA_HOME` 环境变量指向该目录
2. 或在 IDE 中配置项目使用该 Java 版本

## 验证

安装完成后，运行：

```bash
cd KOOKBot
gradlew.bat build
```

如果构建成功，说明 Java 版本已正确配置。

