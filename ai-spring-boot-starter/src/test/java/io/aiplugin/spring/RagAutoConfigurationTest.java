package io.aiplugin.spring;

import io.aiplugin.core.AiRequest;
import io.aiplugin.core.AiResponse;
import io.aiplugin.core.provider.AiProvider;
import io.aiplugin.core.rag.RagService;
import io.aiplugin.core.rag.embedding.EmbeddingModel;
import io.aiplugin.spring.annotation.AiPrompt;
import io.aiplugin.spring.annotation.AiService;
import io.aiplugin.spring.annotation.EnableAiServices;
import io.aiplugin.spring.autoconfigure.AiAutoConfiguration;
import io.aiplugin.spring.autoconfigure.RagAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class RagAutoConfigurationTest {

    @AiService(provider = "fake")
    public interface HrAi {
        @AiPrompt("{question}")
        String ask(String question);

        @AiPrompt(value = "{question}", useRag = false)
        String askWithoutRag(String question);
    }

    static class RecordingProvider implements AiProvider {
        final List<AiRequest> requests = new ArrayList<>();

        public String name() {
            return "fake";
        }

        public AiResponse complete(AiRequest r) {
            requests.add(r);
            return new AiResponse("ok", "fake", "m", 0, 0);
        }
    }

    /** bigram 해싱 임베딩 (외부 API 없이 테스트). */
    static class TestEmbedding implements EmbeddingModel {
        public String id() {
            return "test/hashing";
        }

        public List<float[]> embed(List<String> texts) {
            return texts.stream().map(t -> {
                float[] v = new float[128];
                String s = t.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
                for (int i = 0; i + 2 <= s.length(); i++) {
                    v[Math.floorMod(s.substring(i, i + 2).hashCode(), 128)]++;
                }
                return v;
            }).toList();
        }
    }

    @Configuration
    @EnableAiServices(basePackageClasses = RagAutoConfigurationTest.class)
    static class Config {
        @Bean
        RecordingProvider recordingProvider() {
            return new RecordingProvider();
        }

        @Bean
        EmbeddingModel testEmbedding() {
            return new TestEmbedding();
        }
    }

    @Test
    void indexesDocumentsOnStartupAndInjectsSearchResults(@TempDir Path dir) {
        Path storeFile = dir.resolve("store.json");
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AiAutoConfiguration.class, RagAutoConfiguration.class))
                .withUserConfiguration(Config.class)
                .withPropertyValues("ai.rag.enabled=true", "ai.rag.min-score=0.05",
                        "ai.rag.store.file-path=" + storeFile)
                .run(ctx -> {
                    ApplicationArguments args = new DefaultApplicationArguments();
                    for (ApplicationRunner runner : ctx.getBeansOfType(ApplicationRunner.class).values()) {
                        runner.run(args);
                    }
                    RagService rag = ctx.getBean(RagService.class);
                    assertThat(rag.store().sources()).containsOnlyKeys("rag-docs/vacation-policy.txt");
                    assertThat(Files.exists(storeFile)).isTrue();

                    HrAi ai = ctx.getBean(HrAi.class);
                    RecordingProvider provider = ctx.getBean(RecordingProvider.class);
                    ai.ask("연차 이월은 며칠까지 가능해?");
                    assertThat(provider.requests.get(0).systemPrompt()).contains("최대 5일까지 이월")
                            .contains("rag-docs/vacation-policy.txt");

                    ai.askWithoutRag("연차 이월은 며칠까지 가능해?");
                    assertThat(provider.requests.get(1).systemPrompt()).doesNotContain("vacation-policy");
                });
    }
}
