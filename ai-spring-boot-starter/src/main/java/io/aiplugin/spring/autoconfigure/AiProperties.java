package io.aiplugin.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml 의 {@code ai.*} 설정. API 키가 비어 있는 프로바이더는 등록되지 않습니다. */
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** 프로바이더를 지정하지 않은 호출에 사용할 기본 프로바이더 (openai, claude, abacus). */
    private String defaultProvider = "claude";

    private final OpenAi openai = new OpenAi();
    private final Claude claude = new Claude();
    private final Abacus abacus = new Abacus();
    private final Knowledge knowledge = new Knowledge();

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public OpenAi getOpenai() {
        return openai;
    }

    public Claude getClaude() {
        return claude;
    }

    public Abacus getAbacus() {
        return abacus;
    }

    public Knowledge getKnowledge() {
        return knowledge;
    }

    public static class ProviderProperties {
        /** API 키. 환경변수 참조 권장 (예: ${OPENAI_API_KEY:}). */
        private String apiKey;
        /** 기본 모델 ID. */
        private String model;
        /** 최대 출력 토큰. */
        private int maxTokens = 16000;
        /** 요청 타임아웃(초). */
        private int timeoutSeconds = 300;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public static class OpenAi extends ProviderProperties {
        private String baseUrl = "https://api.openai.com/v1";

        public OpenAi() {
            setModel("gpt-5");
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class Claude extends ProviderProperties {
        /** 추론 강도: low, medium, high, xhigh, max. */
        private String effort = "medium";
        /** 안전 분류기 거절 시 서버 측 fallback 모델로 재시도. */
        private boolean fallbackEnabled = true;

        public Claude() {
            setModel("claude-opus-5-5");
        }

        public String getEffort() {
            return effort;
        }

        public void setEffort(String effort) {
            this.effort = effort;
        }

        public boolean isFallbackEnabled() {
            return fallbackEnabled;
        }

        public void setFallbackEnabled(boolean fallbackEnabled) {
            this.fallbackEnabled = fallbackEnabled;
        }
    }

    public static class Abacus extends ProviderProperties {
        /** Abacus.AI RouteLLM (OpenAI 호환) 엔드포인트. */
        private String baseUrl = "https://routellm.abacus.ai/v1";

        public Abacus() {
            setModel("route-llm");
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class Knowledge {
        private boolean enabled = true;
        /** 학습 데이터 위치 (Spring 리소스 패턴). */
        private String location = "classpath*:ai-knowledge/**/*.*";
        private int maxExamples = 5;
        private int maxDocuments = 3;
        private int maxDocumentChars = 8000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public int getMaxExamples() {
            return maxExamples;
        }

        public void setMaxExamples(int maxExamples) {
            this.maxExamples = maxExamples;
        }

        public int getMaxDocuments() {
            return maxDocuments;
        }

        public void setMaxDocuments(int maxDocuments) {
            this.maxDocuments = maxDocuments;
        }

        public int getMaxDocumentChars() {
            return maxDocumentChars;
        }

        public void setMaxDocumentChars(int maxDocumentChars) {
            this.maxDocumentChars = maxDocumentChars;
        }
    }
}
