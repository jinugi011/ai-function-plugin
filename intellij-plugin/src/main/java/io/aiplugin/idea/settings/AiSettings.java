package io.aiplugin.idea.settings;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 플러그인 설정. 모델/기본 프로바이더는 XML 에, API 키는 IDE PasswordSafe(키체인)에 저장합니다.
 */
@Service(Service.Level.APP)
@State(name = "AiFunctionPluginSettings", storages = @Storage("aiFunctionPlugin.xml"))
public final class AiSettings implements PersistentStateComponent<AiSettings.State> {

    /** API 키가 필요한 클라우드 프로바이더. */
    public static final List<String> CLOUD_PROVIDERS = List.of("claude", "openai", "abacus");
    /** 로컬 모델을 포함한 전체 프로바이더. */
    public static final List<String> PROVIDERS = List.of("claude", "openai", "abacus", "ollama", "llamacpp");
    public static final String DEFAULT_GGUF_MODEL = "hf:Qwen/Qwen2.5-3B-Instruct-GGUF/qwen2.5-3b-instruct-q4_k_m.gguf";

    public static class State {
        public String defaultProvider = "claude";
        public String claudeModel = "claude-opus-5-5";
        public String claudeEffort = "medium";
        public String openaiModel = "gpt-5";
        public String abacusModel = "route-llm";
        public String knowledgeDir = "src/main/resources/ai-knowledge";
        public String fineTuneBaseModel = "gpt-4.1-mini-2025-04-14";
        public String ollamaBaseUrl = "http://localhost:11434";
        public String ollamaModel = "llama3.2";
        public String llamaModel = DEFAULT_GGUF_MODEL;
        public String llamaModelDir = System.getProperty("user.home") + "/.aiplugin/models";
        public String llamaServerBinary = "llama-server";
        public int llamaPort = 8089;
        public int llamaContextSize = 4096;
        public String ragEmbeddingProvider = "openai";
        public String ragEmbeddingModel = "";
        public String ragEmbeddingBaseUrl = "";
        public String ragStoreType = "file";
        public String ragFilePath = "src/main/resources/ai-rag/vector-store.json.gz";
        public String ragQdrantUrl = "http://localhost:6333";
        public String ragQdrantCollection = "ai_documents";
        public int ragChunkSize = 800;
        public int ragTopK = 4;
    }

    private State state = new State();

    public static AiSettings getInstance() {
        return ApplicationManager.getApplication().getService(AiSettings.class);
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public String model(String provider) {
        return switch (provider) {
            case "claude" -> state.claudeModel;
            case "openai" -> state.openaiModel;
            case "abacus" -> state.abacusModel;
            case "ollama" -> state.ollamaModel;
            default -> null;
        };
    }

    /** PasswordSafe 접근은 느릴 수 있으므로 백그라운드 스레드에서 호출하세요. */
    public static String getApiKey(String provider) {
        return PasswordSafe.getInstance().getPassword(credentials(provider));
    }

    public static void setApiKey(String provider, String key) {
        PasswordSafe.getInstance().setPassword(credentials(provider), key == null || key.isBlank() ? null : key);
    }

    private static CredentialAttributes credentials(String provider) {
        return new CredentialAttributes(CredentialAttributesKt.generateServiceName("AI Function Plugin", provider));
    }
}
