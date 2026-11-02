plugins {
    java
    id("org.springframework.boot")
}

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
tasks.withType<JavaCompile> { options.encoding = "UTF-8"; options.compilerArgs.add("-parameters") }

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.2"))
    implementation(project(":ai-spring-boot-starter"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
}
