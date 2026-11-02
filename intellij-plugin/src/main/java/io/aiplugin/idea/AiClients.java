package io.aiplugin.idea;

import com.intellij.openapi.project.Project;
import io.aiplugin.core.AiClient;
import io.aiplugin.core.knowledge.KnowledgeBase;
import io.aiplugin.core.local.OllamaClient;
import io.aiplugin.core.local.OllamaProvider;
import io.aiplugin.core.provider.AiProvider;
import io.aiplugin.core.provider.ClaudeProvider;
import io.aiplugin.core.provider.OpenAiCompatibleProvider;
import io.aiplugin.idea.settings.AiSettings;

import java.nio.file.Path;
import java.time.Duration;
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
        // 로컬 모델은 API 키가 필요 없으므로 항상 등록 (실제 연결은 호출 시점에)
        providers.add(new OllamaProvider(new OllamaClient(s.ollamaBaseUrl), s.ollamaModel, 2048, true,
                Duration.ofMinutes(10), null));
        providers.add(LocalModelService.getInstance().llamaCpp());
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
