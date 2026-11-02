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

    public static final List<String> PROVIDERS = List.of("claude", "openai", "abacus");

    public static class State {
        public String defaultProvider = "claude";
        public String claudeModel = "claude-opus-5-5";
        public String claudeEffort = "medium";
        public String openaiModel = "gpt-5";
        public String abacusModel = "route-llm";
        public String knowledgeDir = "src/main/resources/ai-knowledge";
        public String fineTuneBaseModel = "gpt-4.1-mini-2025-04-14";
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
