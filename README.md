# AI Function Plugin

Spring Boot 애플리케이션과 IntelliJ에서 OpenAI, Claude, Abacus.AI 모델을 **메서드(함수) 단위로** 호출하는 플러그인입니다.

| 모듈 | 설명 |
|---|---|
| `ai-core` | 프로바이더 연동(OpenAI, Claude, Abacus), 프롬프트 템플릿, TEXT / JSON / key-value 응답 파싱, 지식베이스(사전 학습 데이터), OpenAI 파인튜닝 |
| `ai-spring-boot-starter` | `@AiService` 인터페이스와 `@AiPrompt` 메서드를 선언하면 구현체를 자동 생성. `application.yml`에서 설정 |
| `intellij-plugin` | IntelliJ 툴윈도우에서 프롬프트 실행, API 키 관리, 학습 데이터 편집, `@AiService` 코드 생성 |
| `sample-app` | Spring Boot 사용 예제 |

## 빌드

```bash
./gradlew build                              # 전체 빌드 + 테스트
./gradlew publishToMavenLocal                # ai-core, starter 를 ~/.m2 에 설치
./gradlew :intellij-plugin:buildPlugin       # intellij-plugin/build/distributions/*.zip
./gradlew :intellij-plugin:runIde            # 플러그인이 설치된 샌드박스 IDE 실행
```

IntelliJ 플러그인은 `gradle.properties`의 `platformLocalPath`(로컬 IntelliJ)를 대상으로 빌드합니다. 값을 비우면 `platformVersion` 버전을 내려받아 사용합니다.

## 1. Spring에서 사용하기

### 의존성 (JitPack)

GitHub 태그를 [JitPack](https://jitpack.io)이 빌드해서 Maven 저장소로 제공합니다.

```kotlin
// build.gradle.kts
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}
dependencies {
    implementation("com.github.jinugi011.ai-function-plugin:ai-spring-boot-starter:v0.1.0")
    // Spring 없이 코어만: com.github.jinugi011.ai-function-plugin:ai-core:v0.1.0
}
```

```xml
<!-- Maven pom.xml -->
<repositories>
  <repository><id>jitpack.io</id><url>https://jitpack.io</url></repository>
</repositories>
<dependency>
  <groupId>com.github.jinugi011.ai-function-plugin</groupId>
  <artifactId>ai-spring-boot-starter</artifactId>
  <version>v0.1.0</version>
</dependency>
```

새 버전을 배포하려면 GitHub에 태그를 push하면 됩니다(`git tag v0.2.0 && git push origin v0.2.0`). 로컬에서만 쓸 때는 `./gradlew publishToMavenLocal` 후 `io.aiplugin:ai-spring-boot-starter:0.1.0`을 `mavenLocal()`에서 받을 수 있습니다.

컴파일 옵션에 `-parameters`를 켜면 `{text}` 같은 템플릿 변수를 파라미터 이름과 바로 연결합니다. 이 옵션 없이 쓰려면 파라미터에 `@PromptVar("text")`를 붙이세요.

### 설정 (`application.yml`)

```yaml
ai:
  default-provider: claude          # claude | openai | abacus
  claude:
    api-key: ${ANTHROPIC_API_KEY:}
    model: claude-opus-5-5
    effort: medium                  # low | medium | high | xhigh | max
    fallback-enabled: true          # 안전 분류기가 거절하면 서버 측 대체 모델로 재시도
  openai:
    api-key: ${OPENAI_API_KEY:}
    model: gpt-5
  abacus:
    api-key: ${ABACUS_API_KEY:}
    model: route-llm
    base-url: https://routellm.abacus.ai/v1
  knowledge:
    location: classpath*:ai-knowledge/**/*.*
    max-examples: 5
    max-documents: 3
```

API 키가 설정된 프로바이더만 등록됩니다. `AiProvider`를 구현한 빈을 직접 등록하면 사내 LLM 같은 다른 서비스도 함께 쓸 수 있습니다.

### 함수 단위 호출: `@AiService`

```java
@AiService(system = "당신은 쇼핑몰 주문 처리 도우미입니다.")
public interface OrderAi {

    record Order(String customer, String address, List<OrderItem> items) {}
    record OrderItem(String product, int quantity, Integer unitPrice) {}

    @AiPrompt("다음 주문 메시지를 구조화해줘:\n{message}")
    Order parseOrder(String message);                    // JSON → Order

    @AiPrompt("고객 문의의 카테고리, 긴급도, 요약:\n{inquiry}")
    Map<String, String> classify(String inquiry);        // key-value

    @AiPrompt(value = "{name} 마케팅 문구 3개", provider = "openai")
    JsonNode copies(String name);                        // 자유 형식 JSON

    @AiPrompt
    String ask(String prompt);                           // 파라미터가 곧 프롬프트
}
```

`@SpringBootApplication` 패키지 아래에 있는 `@AiService` 인터페이스는 자동으로 빈으로 등록됩니다. 다른 패키지에 있다면 `@EnableAiServices(basePackages = "...")`로 지정하세요.

응답 형식은 반환 타입으로 정해집니다.

| 반환 타입 | 요청 형식 | 결과 |
|---|---|---|
| `String` | TEXT | 원문 |
| `Map<String, String>` | KEY_VALUE | 평면 Map (중첩 JSON은 `a.b`, `list[0]` 형태로 펼침) |
| `JsonNode` | JSON | Jackson 트리 |
| record, 클래스, `List<T>` | JSON | 해당 타입으로 매핑. 타입 구조가 모델 지시문에 자동으로 포함됨 |
| `AiResult` | TEXT | `.text()`, `.json()`, `.keyValue()`, `.as(...)`를 직접 호출 |

`@AiPrompt(format = PromptFormat.JSON)`처럼 형식을 직접 지정할 수도 있습니다. 모델이 코드펜스(```json)나 앞뒤 설명을 붙여 응답해도 파서가 JSON 부분만 골라냅니다.

### 직접 호출: `AiClient`

```java
Map<String, String> kv = aiClient.prompt("{text} 에서 이름/나이/도시 추출")
        .var("text", "홍길동은 30살이고 서울에 산다")
        .provider("claude")          // 생략하면 default-provider
        .keyValue();                 // .text() / .json() / .as(Person.class)
```

## 2. 사전 학습 데이터 (`ai-knowledge`)

API로 제공되는 상용 모델은 가중치를 직접 사전학습(pre-training)할 수 없습니다. 이 플러그인은 대신 두 가지 방법으로 도메인 데이터를 모델에 반영합니다.

1. **컨텍스트 주입** (모든 프로바이더): `src/main/resources/ai-knowledge/` 아래 파일을 시작할 때 읽어 둡니다. 요청이 들어오면 질의와 비슷한 예제와 문서를 골라 시스템 프롬프트에 넣습니다. Claude에서는 이 부분에 프롬프트 캐시를 적용합니다.
2. **파인튜닝** (OpenAI): 학습 예제를 OpenAI 형식 JSONL로 내보내고 파인튜닝 작업을 만듭니다. 작업이 끝나면 결과 모델(`ft:...`)을 `ai.openai.model`에 지정합니다.

| 파일 | 용도 |
|---|---|
| `*.jsonl`, `*.json` | 학습 예제 `{"input": "...", "output": {...} 또는 "...", "tags": ["order"]}` |
| `*.md`, `*.txt` | 참고 문서 (용어집, 업무 규칙, 코드표 등). 질의와 관련 있는 문서만 포함 |
| `instructions*.md` | 모든 요청에 항상 포함되는 공통 규칙 |

`@AiService(knowledgeTag = "order")`로 지정하면 해당 태그가 붙은 예제와 문서만 사용합니다. 특정 메서드에서 지식베이스를 끄려면 `@AiPrompt(useKnowledge = false)`를 지정하세요.

```java
@Autowired OpenAiFineTuneService fineTune;   // ai.openai.api-key 가 있을 때 등록됨
JsonNode job = fineTune.startFineTune(knowledgeBase.examples(), null, "gpt-4.1-mini-2025-04-14", "orders");
fineTune.getJob(job.path("id").asText());
```

## 3. IntelliJ 플러그인

설치: **Settings → Plugins → ⚙ → Install Plugin from Disk…** 에서 `intellij-plugin/build/distributions/intellij-plugin-0.1.0.zip`을 선택합니다.

- **Settings → Tools → AI Function Plugin**: API 키(IDE 비밀번호 저장소에 저장), 모델, 기본 프로바이더, 지식 디렉터리
- **AI Function 툴윈도우 → Prompt 탭**: 프로바이더와 형식(TEXT / JSON / KEY_VALUE)을 고르고 프롬프트를 실행합니다. 실행 결과는 "Save as Training Example" 버튼으로 학습 예제에 추가할 수 있습니다.
- **Training Data 탭**: `ai-knowledge/examples.jsonl` 편집, OpenAI JSONL 내보내기, 파인튜닝 시작과 상태 확인
- **에디터 우클릭 → Send Selection to AI Prompt**: 선택한 코드나 텍스트를 프롬프트로 보냅니다.
- **프로젝트 뷰 → New → AI Service Interface**: `@AiService` 인터페이스 템플릿을 생성합니다.

## 샘플 앱 실행

```bash
ANTHROPIC_API_KEY=sk-ant-... ./gradlew :sample-app:bootRun

curl localhost:8080/providers
curl -X POST localhost:8080/orders/parse -H 'Content-Type: text/plain' \
     -d '이영희입니다. 부산 해운대구 우동 1번지로 배 3박스 보내주세요'
curl -X POST 'localhost:8080/prompt?format=KEY_VALUE' -H 'Content-Type: text/plain' \
     -d '대한민국의 수도와 인구를 알려줘'
```

## 참고

- Abacus.AI는 RouteLLM의 OpenAI 호환 엔드포인트(`https://routellm.abacus.ai/v1`)를 사용합니다. 계정에서 다른 엔드포인트를 쓴다면 `ai.abacus.base-url`을 바꾸세요.
- OpenAI 파인튜닝에는 최소 10개의 예제가 필요하며, 파인튜닝을 지원하는 기본 모델을 사용해야 합니다.
