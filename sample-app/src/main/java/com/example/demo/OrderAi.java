package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import io.aiplugin.spring.annotation.AiPrompt;
import io.aiplugin.spring.annotation.AiService;

import java.util.List;
import java.util.Map;

/** 인터페이스만 선언하면 구현체가 자동 생성되어 빈으로 주입됩니다. */
@AiService(system = "당신은 쇼핑몰 주문 처리 도우미입니다.", knowledgeTag = "order")
public interface OrderAi {

    record OrderItem(String product, int quantity, Integer unitPrice) {
    }

    record Order(String customer, String address, List<OrderItem> items) {
    }

    /** 반환 타입이 record → JSON 응답을 Order 로 매핑 */
    @AiPrompt("다음 주문 메시지를 구조화해줘:\n{message}")
    Order parseOrder(String message);

    /** 반환 타입이 Map → key-value 응답 */
    @AiPrompt("다음 고객 문의의 카테고리, 긴급도(high/medium/low), 요약을 알려줘:\n{inquiry}")
    Map<String, String> classify(String inquiry);

    /** 다른 프로바이더 지정 + 반환 타입 JsonNode → 자유 형식 JSON */
    @AiPrompt(value = "{productName} 상품의 마케팅 문구 3개를 JSON 배열 copies 필드로 만들어줘", provider = "openai")
    JsonNode marketingCopies(String productName);

    /** 템플릿 없이 문자열 파라미터 자체가 프롬프트 */
    @AiPrompt
    String ask(String prompt);
}
