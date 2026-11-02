package io.aiplugin.idea;

import com.intellij.openapi.project.Project;
import io.aiplugin.core.AiClient;
import io.aiplugin.core.knowledge.KnowledgeBase;
import io.aiplugin.core.provider.AiProvider;
import io.aiplugin.core.provider.ClaudeProvider;
import io.aiplugin.core.provider.OpenAiCompatibleProvider;
import io.aiplugin.idea.settings.AiSettings;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** IDE 설정으로부터 {@link AiClient} 를 만듭니다. PasswordSafe 를 읽으므로 백그라운드에서 호출하세요. */
public final class AiClients {

    private AiClients() {
    }

    public static AiClient create(Project project, boolean withKnowledge) {
        AiSettings settings = AiSettings.getInstance();
        AiSettings.State s = settings.getState();
        List<AiProvider> providers = new ArrayList<>();
        String claudeKey = AiSettings.getApiKey("claude");
        if (claudeKey != null) {
            providers.add(new ClaudeProvider(claudeKey, s.claudeModel, 16000, s.claudeEffort, true));
        }
        String openaiKey = AiSettings.getApiKey("openai");
        if (openaiKey != null) {
            providers.add(OpenAiCompatibleProvider.openAi(openaiKey, s.openaiModel));
        }
        String abacusKey = AiSettings.getApiKey("abacus");
        if (abacusKey != null) {
            providers.add(OpenAiCompatibleProvider.abacus(abacusKey, s.abacusModel));
        }
        if (providers.isEmpty()) {
            throw new IllegalStateException("No API key configured. Open Settings | Tools | AI Function Plugin.");
        }
        KnowledgeBase kb = withKnowledge ? loadKnowledge(project) : KnowledgeBase.empty();
        return new AiClient(providers, s.defaultProvider, kb, null);
    }

    public static Path knowledgeDir(Project project) {
        return Path.of(project.getBasePath()).resolve(AiSettings.getInstance().getState().knowledgeDir);
    }

    public static KnowledgeBase loadKnowledge(Project project) {
        return KnowledgeBase.builder().loadDirectory(knowledgeDir(project)).build();
    }
}
