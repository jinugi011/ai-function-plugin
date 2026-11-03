package io.aiplugin.core;

import io.aiplugin.core.knowledge.KnowledgeBase;
import io.aiplugin.core.parse.ResponseParser;
import io.aiplugin.core.provider.AiProvider;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 여러 AI 프로바이더를 하나로 묶은 진입점.
 *
 * <pre>{@code
 * Map<String, String> kv = aiClient.prompt("다음 문장에서 이름과 나이를 추출: {text}")
 *         .var("text", "홍길동은 30살이다")
 *         .provider("claude")
 *         .keyValue();
 * }</pre>
 */
public class AiClient {

    private final Map<String, AiProvider> providers = new LinkedHashMap<>();
    private final String defaultProvider;
    private final KnowledgeBase knowledgeBase;
    private final ResponseParser parser;
    private final ContextRetriever retriever;

    public AiClient(Iterable<AiProvider> providers, String defaultProvider, KnowledgeBase knowledgeBase, ResponseParser parser) {
        this(providers, defaultProvider, knowledgeBase, parser, null);
    }

    /**
     * @param retriever RAG 검색기 (예: {@link io.aiplugin.core.rag.RagService}). null 이면 RAG 미사용.
     */
    public AiClient(Iterable<AiProvider> providers, String defaultProvider, KnowledgeBase knowledgeBase,
                    ResponseParser parser, ContextRetriever retriever) {
        this.retriever = retriever;
        for (AiProvider p : providers) {
            this.providers.put(p.name(), p);
        }
        if (this.providers.isEmpty()) {
            throw new IllegalArgumentException("At least one AI provider must be configured");
        }
        this.defaultProvider = defaultProvider != null && this.providers.containsKey(defaultProvider)
                ? defaultProvider : this.providers.keySet().iterator().next();
        this.knowledgeBase = knowledgeBase != null ? knowledgeBase : KnowledgeBase.empty();
        this.parser = parser != null ? parser : new ResponseParser();
    }

    public Set<String> providerNames() {
        return Collections.unmodifiableSet(providers.keySet());
    }

    public String defaultProvider() {
        return defaultProvider;
    }

    public KnowledgeBase knowledgeBase() {
        return knowledgeBase;
    }

    public ResponseParser parser() {
        return parser;
    }

    /** RAG 검색기, 없으면 null. */
    public ContextRetriever retriever() {
        return retriever;
    }

    public PromptCall prompt(String template) {
        return new PromptCall(this, template);
    }

    AiProvider provider(String name) {
        String key = name == null || name.isBlank() ? defaultProvider : name;
        AiProvider p = providers.get(key);
        if (p == null) {
            throw new AiException("AI provider '" + key + "' is not configured. Available: " + providers.keySet());
        }
        return p;
    }

    AiResult execute(String providerName, AiRequest request) {
        return new AiResult(provider(providerName).complete(request), parser);
    }
}
