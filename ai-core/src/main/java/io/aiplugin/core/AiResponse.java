package io.aiplugin.core;

/** 프로바이더가 돌려준 원본 응답. 파싱은 {@link AiResult} 에서 합니다. */
public record AiResponse(String text, String provider, String model, long inputTokens, long outputTokens) {
}
