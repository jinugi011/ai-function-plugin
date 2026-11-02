package io.aiplugin.core.local;

import io.aiplugin.core.AiRequest;
import io.aiplugin.core.AiResponse;
import io.aiplugin.core.provider.AiProvider;
import io.aiplugin.core.provider.OpenAiCompatibleProvider;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * llama.cpp 로 GGUF 모델을 직접 내려받아 실행하는 프로바이더.
 *
 * <p>첫 호출(또는 {@link #prepare()}) 때 모델 다운로드 → {@code llama-server} 실행 → 준비 대기를 한 번만 수행합니다.
 * {@code baseUrl} 을 지정하면 다운로드/실행 없이 이미 떠 있는 서버를 사용합니다.
 *
 * <pre>{@code
 * try (LlamaCppProvider llama = LlamaCppProvider.builder()
 *         .model("hf:bartowski/Llama-3.2-3B-Instruct-GGUF/Llama-3.2-3B-Instruct-Q4_K_M.gguf")
 *         .build()) {
 *     AiClient client = new AiClient(List.of(llama), "llamacpp", null, null);
 *     client.prompt("안녕").text();
 * }
 * }</pre>
 */
public class LlamaCppProvider implements AiProvider, AutoCloseable {

    private final Builder config;
    private LlamaCppServer server;
    private volatile OpenAiCompatibleProvider delegate;

    private LlamaCppProvider(Builder config) {
        this.config = config;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String name() {
        return "llamacpp";
    }

    /** 모델 다운로드와 서버 시작을 미리 수행합니다 (첫 요청 지연을 없애고 싶을 때). */
    public synchronized void prepare() {
        if (delegate != null) {
            return;
        }
        String baseUrl = config.baseUrl;
        String modelName = "local";
        if (baseUrl == null || baseUrl.isBlank()) {
            if (config.model == null || config.model.isBlank()) {
                throw new IllegalStateException("llamacpp: set model (hf:owner/repo/file.gguf, URL or file path) or base-url");
            }
            Path modelPath = new ModelDownloader(config.huggingFaceToken)
                    .download(config.model, config.modelDir, config.progressListener);
            server = new LlamaCppServer(config.serverBinary, modelPath, config.host, config.port,
                    config.contextSize, config.gpuLayers, config.extraArgs, config.startupTimeout);
            server.start();
            baseUrl = server.baseUrl();
            modelName = modelPath.getFileName().toString();
        }
        delegate = new OpenAiCompatibleProvider(name(), baseUrl, null, modelName, config.maxTokens,
                config.requestTimeout, "max_tokens");
    }

    public synchronized boolean isServerRunning() {
        return server != null && server.isRunning();
    }

    @Override
    public AiResponse complete(AiRequest request) {
        if (delegate == null) {
            prepare();
        }
        // llama-server 는 로드된 모델 하나만 서비스하므로 모델 이름은 무시합니다
        return delegate.complete(new AiRequest(request.systemPrompt(), request.userPrompt(), request.format(),
                null, request.maxTokens()));
    }

    @Override
    public synchronized void close() {
        if (server != null) {
            server.close();
            server = null;
        }
        delegate = null;
    }

    public static final class Builder {
        private String model;
        private Path modelDir = Path.of(System.getProperty("user.home"), ".aiplugin", "models");
        private String baseUrl;
        private String serverBinary = "llama-server";
        private String host = "127.0.0.1";
        private int port = 8089;
        private int contextSize = 4096;
        private Integer gpuLayers;
        private List<String> extraArgs = List.of();
        private int maxTokens = 2048;
        private Duration startupTimeout = Duration.ofMinutes(3);
        private Duration requestTimeout = Duration.ofMinutes(10);
        private String huggingFaceToken;
        private Consumer<DownloadProgress> progressListener;

        /** hf:owner/repo/file.gguf, https URL, 또는 로컬 .gguf 경로. */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder modelDir(Path modelDir) {
            this.modelDir = modelDir;
            return this;
        }

        /** 이미 실행 중인 llama-server 주소 (예: http://localhost:8080/v1). 지정하면 다운로드/실행을 하지 않습니다. */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder serverBinary(String serverBinary) {
            this.serverBinary = serverBinary;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder contextSize(int contextSize) {
            this.contextSize = contextSize;
            return this;
        }

        /** GPU 로 올릴 레이어 수 (-ngl). null 이면 llama.cpp 기본값. */
        public Builder gpuLayers(Integer gpuLayers) {
            this.gpuLayers = gpuLayers;
            return this;
        }

        public Builder extraArgs(List<String> extraArgs) {
            this.extraArgs = extraArgs == null ? List.of() : extraArgs;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder startupTimeout(Duration startupTimeout) {
            this.startupTimeout = startupTimeout;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public Builder huggingFaceToken(String huggingFaceToken) {
            this.huggingFaceToken = huggingFaceToken;
            return this;
        }

        public Builder progressListener(Consumer<DownloadProgress> progressListener) {
            this.progressListener = progressListener;
            return this;
        }

        public LlamaCppProvider build() {
            return new LlamaCppProvider(this);
        }
    }
}
