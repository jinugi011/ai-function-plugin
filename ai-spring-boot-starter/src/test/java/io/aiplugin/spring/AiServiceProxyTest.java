package io.aiplugin.spring;

import io.aiplugin.core.AiRequest;
import io.aiplugin.core.AiResponse;
import io.aiplugin.core.ResponseFormat;
import io.aiplugin.core.provider.AiProvider;
import io.aiplugin.spring.annotation.EnableAiServices;
import io.aiplugin.spring.autoconfigure.AiAutoConfiguration;
import io.aiplugin.spring.sample.ProductAi;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiServiceProxyTest {

    static class FakeProvider implements AiProvider {
        final List<AiRequest> requests = new ArrayList<>();

        @Override
        public String name() {
            return "fake";
        }

        @Override
        public AiResponse complete(AiRequest request) {
            requests.add(request);
            String text = switch (request.format()) {
                case JSON -> "```json\n{\"name\":\"노트북\",\"price\":1000,\"tags\":[\"it\"]}\n```";
                case KEY_VALUE -> "{\"title\":\"요약\",\"lang\":\"ko\"}";
                case TEXT -> "echo: " + request.userPrompt();
            };
            return new AiResponse(text, "fake", "fake-model", 1, 1);
        }
    }

    @Configuration
    @EnableAiServices(basePackageClasses = ProductAi.class)
    static class TestConfig {
        @Bean
        FakeProvider fakeProvider() {
            return new FakeProvider();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AiAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues("ai.default-provider=fake");

    @Test
    void proxiesMethodsByReturnType() {
        runner.run(ctx -> {
            ProductAi ai = ctx.getBean(ProductAi.class);
            FakeProvider fake = ctx.getBean(FakeProvider.class);

            assertThat(ai.extract("노트북 1000원")).isEqualTo(new ProductAi.Product("노트북", 1000, List.of("it")));
            AiRequest req = fake.requests.get(0);
            assertThat(req.userPrompt()).isEqualTo("Extract product from: 노트북 1000원");
            assertThat(req.format()).isEqualTo(ResponseFormat.JSON);
            assertThat(req.systemPrompt()).contains("product assistant").contains("\"price\": number")
                    .contains("<example>"); // test resources/ai-knowledge/examples.jsonl

            assertThat(ai.summarize("긴 글")).isEqualTo(Map.of("title", "요약", "lang", "ko"));
            assertThat(fake.requests.get(1).userPrompt()).isEqualTo("Summarize 긴 글");

            assertThat(ai.ask("안녕")).isEqualTo("echo: 안녕");
        });
    }
}
