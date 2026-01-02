import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.file.DuplicatesStrategy

plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    implementation(project(":infra-api"))
    implementation(project(":infra-core"))

    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")

    // CrownLib (ServiceRegistry exposure for infra services)
    compileOnly("com.github.jojoRy:Crown-Lib:1.0.3")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<Jar>("jar") {
    archiveClassifier.set("plain")
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")

    val relocations = mapOf(
        "com.zaxxer.hikari" to "kr.crownrpg.infra.libs.hikari",
        "com.mysql" to "kr.crownrpg.infra.libs.mysql",
        "io.lettuce" to "kr.crownrpg.infra.libs.lettuce",
        "com.fasterxml.jackson" to "kr.crownrpg.infra.libs.jackson",
        "io.netty" to "kr.crownrpg.infra.libs.netty",
        "org.yaml.snakeyaml" to "kr.crownrpg.infra.libs.snakeyaml"
    )

    relocations.forEach { (from, to) -> relocate(from, to) }
}

tasks.named("assemble") {
    dependsOn(tasks.named("shadowJar"))
}
