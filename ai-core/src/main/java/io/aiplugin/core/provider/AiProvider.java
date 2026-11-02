package io.aiplugin.core.provider;

import io.aiplugin.core.AiRequest;
import io.aiplugin.core.AiResponse;

/** AI 서비스 하나(OpenAI, Claude, Abacus 등)에 대한 연결. */
public interface AiProvider {

    /** 레지스트리에서 사용하는 이름 (예: "openai", "claude", "abacus"). */
    String name();

    AiResponse complete(AiRequest request);
}
