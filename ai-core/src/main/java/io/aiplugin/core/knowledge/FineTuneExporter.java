package io.aiplugin.core.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** 학습 예제를 OpenAI 파인튜닝용 chat JSONL 형식으로 변환합니다. */
public final class FineTuneExporter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FineTuneExporter() {
    }

    /**
     * @param systemPrompt 각 학습 샘플에 넣을 시스템 지시문 (null 가능)
     */
    public static String toOpenAiJsonl(List<TrainingExample> examples, String systemPrompt) {
        StringBuilder sb = new StringBuilder();
        for (TrainingExample e : examples) {
            ObjectNode line = MAPPER.createObjectNode();
            ArrayNode messages = line.putArray("messages");
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                messages.addObject().put("role", "system").put("content", systemPrompt);
            }
            messages.addObject().put("role", "user").put("content", e.input());
            messages.addObject().put("role", "assistant").put("content", e.outputText());
            try {
                sb.append(MAPPER.writeValueAsString(line)).append('\n');
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
        return sb.toString();
    }
}
