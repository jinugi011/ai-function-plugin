rootProject.name = "ai-function-plugin"

include("ai-core", "ai-spring-boot-starter", "sample-app")

// JitPack 에서는 라이브러리만 빌드합니다 (IntelliJ 플랫폼 다운로드 방지)
if (System.getenv("JITPACK") != "true") {
    include("intellij-plugin")
}
