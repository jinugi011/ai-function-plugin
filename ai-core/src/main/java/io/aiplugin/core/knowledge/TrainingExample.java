package io.aiplugin.core.knowledge;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * 입력 → 기대 출력 한 쌍. few-shot 예제로 프롬프트에 주입되거나 파인튜닝 데이터로 내보내집니다.
 *
 * @param input  사용자 입력(프롬프트)
 * @param output 기대 출력 (문자열 또는 JSON)
 * @param tags   분류용 태그 (예: "product", "address")
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TrainingExample(String input, JsonNode output, List<String> tags) {

    public TrainingExample {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public String outputText() {
        return output == null ? "" : output.isTextual() ? output.asText() : output.toString();
    }
}
