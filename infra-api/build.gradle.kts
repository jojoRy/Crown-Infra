plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    // 없음 (계약만)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
