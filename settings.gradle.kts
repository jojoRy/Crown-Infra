rootProject.name = "Crown-Infra"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://jitpack.io")
    }
}

include(
    "infra-api",
    "infra-core",
    "infra-paper",
    "infra-velocity"
)
