plugins {
    id("org.springframework.boot") version "4.1.2" apply false
    id("org.jetbrains.intellij.platform") version "2.19.0" apply false
}

// JitPack 빌드 시 좌표: com.github.<user>.<repo>:<module>:<tag>
val jitpack = System.getenv("JITPACK") == "true"

allprojects {
    if (jitpack) {
        group = "${System.getenv("GROUP")}.${System.getenv("ARTIFACT")}"
        version = System.getenv("VERSION")
    }
}

subprojects {
    repositories {
        mavenCentral()
    }
}
