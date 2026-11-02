package io.aiplugin.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml 의 {@code ai.*} 설정. API 키가 비어 있는 프로바이더는 등록되지 않습니다. */
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** 프로바이더를 지정하지 않은 호출에 사용할 기본 프로바이더 (openai, claude, abacus, ollama, llamacpp). */
    private String defaultProvider = "claude";

    private final OpenAi openai = new OpenAi();
    private final Claude claude = new Claude();
    private final Abacus abacus = new Abacus();
    private final Knowledge knowledge = new Knowledge();
    private final Ollama ollama = new Ollama();
    private final LlamaCpp llamacpp = new LlamaCpp();

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

    public Ollama getOllama() {
        return ollama;
    }

    public LlamaCpp getLlamacpp() {
        return llamacpp;
    }

    /** Ollama 로컬 모델. Ollama 앱/서버가 실행 중이어야 합니다. */
    public static class Ollama {
        private boolean enabled = false;
        private String baseUrl = "http://localhost:11434";
        /** 모델 이름 (https://ollama.com/library), 예: llama3.2, qwen2.5:7b, gemma3. */
        private String model = "llama3.2";
        /** 모델이 없으면 첫 호출 때 자동 다운로드. */
        private boolean autoPull = true;
        /** 애플리케이션 시작 시 모델을 미리 다운로드. */
        private boolean pullOnStartup = false;
        private int maxTokens = 2048;
        private int timeoutSeconds = 600;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public boolean isAutoPull() {
            return autoPull;
        }

        public void setAutoPull(boolean autoPull) {
            this.autoPull = autoPull;
        }

        public boolean isPullOnStartup() {
            return pullOnStartup;
        }

        public void setPullOnStartup(boolean pullOnStartup) {
            this.pullOnStartup = pullOnStartup;
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
    }

    /** llama.cpp 로 GGUF 모델을 직접 내려받아 llama-server 로 실행. */
    public static class LlamaCpp {
        private boolean enabled = false;
        /** hf:owner/repo/file.gguf, https URL, 또는 로컬 .gguf 경로. */
        private String model;
        /** 모델 저장 위치. */
        private String modelDir = System.getProperty("user.home") + "/.aiplugin/models";
        /** 이미 실행 중인 llama-server 를 쓸 때 (예: http://localhost:8080/v1). 지정하면 다운로드/실행 생략. */
        private String baseUrl;
        /** llama-server 실행 파일 (PATH 에 없으면 전체 경로). */
        private String serverBinary = "llama-server";
        private int port = 8089;
        private int contextSize = 4096;
        /** GPU 레이어 수 (-ngl). 비우면 llama.cpp 기본값. */
        private Integer gpuLayers;
        /** llama-server 추가 인자. */
        private java.util.List<String> extraArgs = new java.util.ArrayList<>();
        private int maxTokens = 2048;
        private int startupTimeoutSeconds = 180;
        /** 시작 시 다운로드 + 서버 실행 (false 면 첫 호출 때). */
        private boolean startOnStartup = false;
        /** 접근 제한 모델용 Hugging Face 토큰. */
        private String huggingFaceToken;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getModelDir() {
            return modelDir;
        }

        public void setModelDir(String modelDir) {
            this.modelDir = modelDir;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getServerBinary() {
            return serverBinary;
        }

        public void setServerBinary(String serverBinary) {
            this.serverBinary = serverBinary;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public int getContextSize() {
            return contextSize;
        }

        public void setContextSize(int contextSize) {
            this.contextSize = contextSize;
        }

        public Integer getGpuLayers() {
            return gpuLayers;
        }

        public void setGpuLayers(Integer gpuLayers) {
            this.gpuLayers = gpuLayers;
        }

        public java.util.List<String> getExtraArgs() {
            return extraArgs;
        }

        public void setExtraArgs(java.util.List<String> extraArgs) {
            this.extraArgs = extraArgs;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public int getStartupTimeoutSeconds() {
            return startupTimeoutSeconds;
        }

        public void setStartupTimeoutSeconds(int startupTimeoutSeconds) {
            this.startupTimeoutSeconds = startupTimeoutSeconds;
        }

        public boolean isStartOnStartup() {
            return startOnStartup;
        }

        public void setStartOnStartup(boolean startOnStartup) {
            this.startOnStartup = startOnStartup;
        }

        public String getHuggingFaceToken() {
            return huggingFaceToken;
        }

        public void setHuggingFaceToken(String huggingFaceToken) {
            this.huggingFaceToken = huggingFaceToken;
        }
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
