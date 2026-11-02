package io.aiplugin.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import io.aiplugin.core.parse.ResponseParser;

import java.util.Map;

/** AI 응답 + 형식별 변환 도우미. */
public final class AiResult {

    private final AiResponse response;
    private final ResponseParser parser;

    AiResult(AiResponse response, ResponseParser parser) {
        this.response = response;
        this.parser = parser;
    }

    public AiResponse response() {
        return response;
    }

    public String text() {
        return response.text();
    }

    public JsonNode json() {
        return parser.toJson(response.text());
    }

    public Map<String, String> keyValue() {
        return parser.toKeyValue(response.text());
    }

    public <T> T as(Class<T> type) {
        return parser.toObject(response.text(), type);
    }

    public <T> T as(TypeReference<T> type) {
        return parser.toObject(response.text(), parser.mapper().getTypeFactory().constructType(type));
    }

    public <T> T as(JavaType type) {
        return parser.toObject(response.text(), type);
    }
}
