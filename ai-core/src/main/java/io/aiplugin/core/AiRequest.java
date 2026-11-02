package io.aiplugin.core;

/**
 * 프로바이더에 전달되는 단일 요청.
 *
 * @param systemPrompt 시스템 지시문 (지식베이스 컨텍스트 포함, null 가능)
 * @param userPrompt   사용자 프롬프트
 * @param format       기대하는 응답 형식
 * @param model        모델 ID (null 이면 프로바이더 기본값)
 * @param maxTokens    최대 출력 토큰 (null 이면 프로바이더 기본값)
 */
public record AiRequest(String systemPrompt, String userPrompt, ResponseFormat format, String model, Integer maxTokens) {

    public AiRequest {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("userPrompt must not be empty");
        }
        if (format == null) {
            format = ResponseFormat.TEXT;
        }
    }

    public static AiRequest of(String userPrompt) {
        return new AiRequest(null, userPrompt, ResponseFormat.TEXT, null, null);
    }
}
