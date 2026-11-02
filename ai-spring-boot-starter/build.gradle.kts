plugins {
    `java-library`
    `maven-publish`
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
    withSourcesJar()
}
tasks.withType<JavaCompile> {
    options.release = 17
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

dependencies {
    api(project(":ai-core"))
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.2"))
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor(platform("org.springframework.boot:spring-boot-dependencies:4.1.2"))
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.2"))
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }

publishing {
    publications { create<MavenPublication>("maven") { from(components["java"]) } }
}
