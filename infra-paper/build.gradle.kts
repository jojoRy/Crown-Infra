plugins {
    java
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(project(":infra-api"))
    implementation(project(":infra-core"))

    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")

    // ✅ Redis (Lettuce)
    implementation("io.lettuce:lettuce-core:6.3.2.RELEASE")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
