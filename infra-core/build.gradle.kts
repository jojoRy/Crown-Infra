plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":infra-api"))

    // ✅ MySQL (Hikari + MySQL Driver) - 이미 쓰고 있다면 유지
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("com.mysql:mysql-connector-j:8.0.33")

    // ✅ Redis PubSub Bus (Lettuce)
    implementation("io.lettuce:lettuce-core:6.3.2.RELEASE")

    // ✅ Message codec (Jackson)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")

    // ✅ Netty realtime channel
    implementation("io.netty:netty-handler:4.1.110.Final")

    // ✅ Logging (SLF4J API only, 구현체는 플랫폼에서 제공)
    implementation("org.slf4j:slf4j-api:1.7.36")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
