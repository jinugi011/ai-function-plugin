import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    java
    id("org.jetbrains.intellij.platform")
}

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
tasks.withType<JavaCompile> { options.encoding = "UTF-8" }

dependencies {
    implementation(project(":ai-core"))

    intellijPlatform {
        val localIde = providers.gradleProperty("platformLocalPath").orNull
        if (!localIde.isNullOrBlank() && file(localIde).exists()) {
            local(localIde)
        } else {
            intellijIdeaCommunity(providers.gradleProperty("platformVersion"))
        }
        testFramework(TestFrameworkType.Platform)
    }
}

// IntelliJ 가 Kotlin 표준 라이브러리를 제공하므로 플러그인에 포함하지 않습니다 (anthropic-java 가 의존).
configurations.named("runtimeClasspath") {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-common")
}

intellijPlatform {
    pluginConfiguration {
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "251"
        }
    }
    buildSearchableOptions = false
}

tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("-Xlint:deprecation") }
