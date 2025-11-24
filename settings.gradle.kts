pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        gradlePluginPortal()
    }
}

include("QQbot")
include("SimpfunPassAPI")
include("KOOKBot")