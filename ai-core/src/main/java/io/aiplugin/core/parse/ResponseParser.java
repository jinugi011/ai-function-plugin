package io.aiplugin.core.parse;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aiplugin.core.AiException;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 모델이 돌려준 텍스트를 JSON / key-value / 자바 객체로 변환합니다.
 * 모델이 코드펜스(```json)나 앞뒤 설명을 붙여도 JSON 부분만 추출합니다.
 */
public final class ResponseParser {

    private static final Pattern FENCE = Pattern.compile("```(?:json|JSON)?\\s*(.*?)```", Pattern.DOTALL);
    private static final Pattern KV_LINE = Pattern.compile("^\\s*[-*]?\\s*\"?([^:=\"]+?)\"?\\s*[:=]\\s*(.*?)\\s*,?\\s*$");

    private final ObjectMapper mapper;

    public ResponseParser() {
        this(new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
    }

    public ResponseParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public JsonNode toJson(String text) {
        String json = extractJson(text);
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new AiException("AI response is not valid JSON: " + abbreviate(text), e);
        }
    }

    /**
     * 평면 key-value Map 으로 변환합니다. JSON 객체이면 중첩 키를 "a.b" / "a[0]" 형태로 펼치고,
     * JSON 이 아니면 "key: value" 또는 "key=value" 줄 단위 형식으로 해석합니다.
     */
    public Map<String, String> toKeyValue(String text) {
        Map<String, String> result = new LinkedHashMap<>();
        String json = extractJson(text);
        try {
            JsonNode node = mapper.readTree(json);
            if (node != null && node.isContainerNode()) {
                flatten("", node, result);
                return result;
            }
        } catch (Exception ignored) {
            // JSON 이 아니면 줄 단위 key-value 로 해석
        }
        for (String line : text.split("\\R")) {
            Matcher m = KV_LINE.matcher(line);
            if (m.matches()) {
                result.put(m.group(1).trim(), stripQuotes(m.group(2)));
            }
        }
        if (result.isEmpty()) {
            throw new AiException("AI response could not be parsed as key-value: " + abbreviate(text));
        }
        return result;
    }

    public <T> T toObject(String text, Class<T> type) {
        return toObject(text, mapper.constructType(type));
    }

    public <T> T toObject(String text, JavaType type) {
        try {
            return mapper.readValue(extractJson(text), type);
        } catch (Exception e) {
            throw new AiException("AI response could not be mapped to " + type + ": " + abbreviate(text), e);
        }
    }

    /** 코드펜스 또는 첫 '{'/'[' 부터 짝이 맞는 닫는 괄호까지를 잘라냅니다. */
    static String extractJson(String text) {
        if (text == null) {
            return "";
        }
        String s = text.trim();
        Matcher fence = FENCE.matcher(s);
        if (fence.find()) {
            s = fence.group(1).trim();
        }
        int start = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{' || c == '[') {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return s;
        }
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (depth == 0) {
                    return s.substring(start, i + 1);
                }
            }
        }
        return s.substring(start);
    }

    private static void flatten(String prefix, JsonNode node, Map<String, String> out) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.properties().iterator();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                flatten(prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey(), e.getValue(), out);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                flatten(prefix + "[" + i + "]", node.get(i), out);
            }
        } else {
            out.put(prefix, node.isNull() ? null : node.asText());
        }
    }

    private static String stripQuotes(String v) {
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
