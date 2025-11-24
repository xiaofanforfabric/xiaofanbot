plugins {
    id("java")
    id("application")
}

group = "com.xiaofan.kookbot"
version = "1.0-SNAPSHOT"

java {
    // 使用 Java 21（系统已安装在 C:\Program Files\Java\jdk-21）
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    
    // 配置 Gradle Toolchain 使用 Java 21
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    
    // 显式配置源代码目录（确保IDE正确识别）
    sourceSets {
        main {
            java {
                srcDirs("src/main/java")
            }
            resources {
                srcDirs("src/main/resources")
            }
        }
        test {
            java {
                srcDirs("src/test/java")
            }
            resources {
                srcDirs("src/test/resources")
            }
        }
    }
}

application {
    mainClass.set("com.xiaofan.kookbot.KookBotMain")
}

repositories {
    mavenCentral()
}

dependencies {
    // OkHttp 用于HTTP请求和WebSocket（和QQbot一样）
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JSON 处理（和QQbot一样）
    implementation("org.json:json:20231013")
    
    // 日志（使用logback支持文件输出）
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    
    // MySQL JDBC驱动（如果需要复用数据库功能）
    implementation("com.mysql:mysql-connector-j:8.3.0")
    
    // 测试
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

// 处理资源文件重复问题
tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

// 配置可执行JAR
tasks.jar {
    manifest {
        attributes(
            "Main-Class" to application.mainClass.get()
        )
    }
    
    // 创建 fat jar（包含所有依赖）
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    archiveBaseName.set("kookbot")
    archiveVersion.set(project.version.toString())
}

// 创建单独的 fat jar 任务
tasks.register<Jar>("fatJar") {
    archiveBaseName.set("kookbot")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("all")
    
    manifest {
        attributes("Main-Class" to application.mainClass.get())
    }
    
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
    with(tasks.jar.get())
    
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

