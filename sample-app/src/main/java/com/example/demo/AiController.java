package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import io.aiplugin.core.AiClient;
import io.aiplugin.core.ResponseFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AiController {

    private final OrderAi orderAi;
    private final AiClient aiClient;

    public AiController(OrderAi orderAi, AiClient aiClient) {
        this.orderAi = orderAi;
        this.aiClient = aiClient;
    }

    @PostMapping("/orders/parse")
    public OrderAi.Order parse(@RequestBody String message) {
        return orderAi.parseOrder(message);
    }

    @PostMapping("/inquiries/classify")
    public Map<String, String> classify(@RequestBody String inquiry) {
        return orderAi.classify(inquiry);
    }

    /** AiClient 를 직접 쓰는 방식: 프롬프트 텍스트 + 프로바이더 + 형식을 런타임에 지정 */
    @PostMapping("/prompt")
    public Object prompt(@RequestBody String prompt,
                         @RequestParam(defaultValue = "") String provider,
                         @RequestParam(defaultValue = "JSON") ResponseFormat format) {
        var result = aiClient.prompt(prompt).provider(provider).call(format);
        return switch (format) {
            case JSON -> result.json();
            case KEY_VALUE -> result.keyValue();
            case TEXT -> Map.of("text", result.text());
        };
    }

    @GetMapping("/providers")
    public Map<String, Object> providers() {
        return Map.of("providers", aiClient.providerNames(), "default", aiClient.defaultProvider(),
                "knowledgeExamples", aiClient.knowledgeBase().examples().size(),
                "knowledgeDocuments", aiClient.knowledgeBase().documents().size());
    }
}
