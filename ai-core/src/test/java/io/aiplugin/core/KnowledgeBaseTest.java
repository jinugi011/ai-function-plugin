package io.aiplugin.core;

import io.aiplugin.core.knowledge.FineTuneExporter;
import io.aiplugin.core.knowledge.KnowledgeBase;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeBaseTest {

    @Test
    void picksRelevantExamplesAndAlwaysIncludesInstructions() {
        String jsonl = """
                {"input":"주소 서울시 강남구 테헤란로 1 을 분해","output":{"city":"서울시","district":"강남구"}}
                {"input":"상품명 노트북 가격 100만원","output":{"product":"노트북","price":1000000}}
                """;
        KnowledgeBase kb = KnowledgeBase.builder()
                .maxExamples(1)
                .load("examples.jsonl", new ByteArrayInputStream(jsonl.getBytes(StandardCharsets.UTF_8)))
                .addDocument("instructions", "금액은 원 단위 정수로 표기한다.")
                .addDocument("배송 정책", "제주도는 추가 배송비 3000원")
                .build();

        String ctx = kb.buildContext("주소 부산시 해운대구 를 분해해줘", null);
        assertTrue(ctx.contains("강남구"));
        assertFalse(ctx.contains("노트북"));
        assertTrue(ctx.contains("원 단위 정수"));
        assertFalse(ctx.contains("제주도"));

        String ft = FineTuneExporter.toOpenAiJsonl(kb.examples(), "sys");
        assertTrue(ft.lines().count() == 2 && ft.contains("\"role\":\"assistant\""));
    }
}
