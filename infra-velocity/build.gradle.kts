plugins {
    java
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    implementation(project(":infra-api"))
    implementation(project(":infra-core"))

    // ✅ Redis (Lettuce)
    implementation("io.lettuce:lettuce-core:6.3.2.RELEASE")

    // ✅ YAML config loader
    implementation("org.yaml:snakeyaml:2.2")

    // ✅ Velocity API (annotation processor 포함)
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")

    // ✅ CrownLib (ServiceRegistry exposure for infra services)
    compileOnly("com.github.jojoRy:Crown-Lib:v1.0.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
