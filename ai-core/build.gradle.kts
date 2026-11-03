plugins {
    `java-library`
    `maven-publish`
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
    withSourcesJar()
}
tasks.withType<JavaCompile> { options.release = 17; options.encoding = "UTF-8" }

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:2.20.0")
    api("com.anthropic:anthropic-java:3.3.0")
    implementation("org.apache.pdfbox:pdfbox:3.0.8")
    testImplementation("org.postgresql:postgresql:42.7.14")

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }

publishing {
    publications { create<MavenPublication>("maven") { from(components["java"]) } }
}
