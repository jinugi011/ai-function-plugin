package io.aiplugin.spring.sample;

import io.aiplugin.spring.annotation.AiPrompt;
import io.aiplugin.spring.annotation.AiService;
import io.aiplugin.spring.annotation.PromptVar;

import java.util.List;
import java.util.Map;

@AiService(provider = "fake", system = "You are a product assistant.")
public interface ProductAi {

    record Product(String name, int price, List<String> tags) {
    }

    @AiPrompt("Extract product from: {text}")
    Product extract(String text);

    @AiPrompt("Summarize {t}")
    Map<String, String> summarize(@PromptVar("t") String text);

    @AiPrompt
    String ask(String prompt);
}
