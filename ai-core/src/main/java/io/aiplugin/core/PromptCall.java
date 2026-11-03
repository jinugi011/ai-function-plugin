package io.aiplugin.core;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

/** {@link AiClient#prompt(String)} 이 반환하는 fluent 빌더. */
public final class PromptCall {

    private final AiClient client;
    private final String template;
    private final Map<String, Object> vars = new HashMap<>();
    private String provider;
    private String model;
    private String system;
    private Integer maxTokens;
    private boolean useKnowledge = true;
    private String knowledgeTag;
    private boolean useRag = true;

    PromptCall(AiClient client, String template) {
        this.client = client;
        this.template = template;
    }

    public PromptCall var(String name, Object value) {
        vars.put(name, value);
        return this;
    }

    public PromptCall vars(Map<String, ?> values) {
        vars.putAll(values);
        return this;
    }

    public PromptCall provider(String provider) {
        this.provider = provider;
        return this;
    }

    public PromptCall model(String model) {
        this.model = model;
        return this;
    }

    public PromptCall system(String system) {
        this.system = system;
        return this;
    }

    public PromptCall maxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
        return this;
    }

    /** 지식베이스(사전 학습 데이터) 사용 여부. 기본값 true. */
    public PromptCall useKnowledge(boolean useKnowledge) {
        this.useKnowledge = useKnowledge;
        return this;
    }

    /** 지정한 태그가 붙은 학습 예제/문서만 컨텍스트로 사용합니다. */
    public PromptCall knowledgeTag(String tag) {
        this.knowledgeTag = tag;
        return this;
    }

    /** RAG(벡터 DB 검색) 사용 여부. 기본값 true (AiClient 에 검색기가 있을 때만 동작). */
    public PromptCall useRag(boolean useRag) {
        this.useRag = useRag;
        return this;
    }

    public AiResult call(ResponseFormat format) {
        String prompt = PromptTemplate.render(template, vars);
        String systemPrompt = system;
        if (useKnowledge) {
            String context = client.knowledgeBase().buildContext(prompt, knowledgeTag);
            if (!context.isEmpty()) {
                systemPrompt = systemPrompt == null ? context : systemPrompt + "\n\n" + context;
            }
        }
        if (useRag && client.retriever() != null) {
            String retrieved = client.retriever().retrieveContext(prompt);
            if (!retrieved.isEmpty()) {
                systemPrompt = systemPrompt == null ? retrieved : systemPrompt + "\n\n" + retrieved;
            }
        }
        return client.execute(provider, new AiRequest(systemPrompt, prompt, format, model, maxTokens));
    }

    public String text() {
        return call(ResponseFormat.TEXT).text();
    }

    public JsonNode json() {
        return call(ResponseFormat.JSON).json();
    }

    public Map<String, String> keyValue() {
        return call(ResponseFormat.KEY_VALUE).keyValue();
    }

    public <T> T as(Class<T> type) {
        return call(ResponseFormat.JSON).as(type);
    }
}
