package io.aiplugin.spring.autoconfigure;

import io.aiplugin.core.AiClient;
import io.aiplugin.core.knowledge.KnowledgeBase;
import io.aiplugin.core.knowledge.OpenAiFineTuneService;
import io.aiplugin.core.parse.ResponseParser;
import io.aiplugin.core.provider.AiProvider;
import io.aiplugin.core.provider.ClaudeProvider;
import io.aiplugin.core.provider.OpenAiCompatibleProvider;
import io.aiplugin.spring.proxy.AiServiceRegistrar;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(AiProperties.class)
public class AiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KnowledgeBase aiKnowledgeBase(AiProperties properties) {
        AiProperties.Knowledge k = properties.getKnowledge();
        KnowledgeBase.Builder builder = KnowledgeBase.builder()
                .maxExamples(k.getMaxExamples())
                .maxDocuments(k.getMaxDocuments())
                .maxDocumentChars(k.getMaxDocumentChars());
        if (!k.isEnabled()) {
            return builder.build();
        }
        try {
            for (Resource r : new PathMatchingResourcePatternResolver().getResources(k.getLocation())) {
                if (r.isReadable() && r.getFilename() != null) {
                    builder.load(r.getFilename(), r.getInputStream());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load AI knowledge from " + k.getLocation(), e);
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ResponseParser aiResponseParser() {
        return new ResponseParser();
    }

    @Bean
    @ConditionalOnMissingBean
    public AiClient aiClient(AiProperties properties, KnowledgeBase knowledgeBase, ResponseParser parser,
                             ObjectProvider<AiProvider> customProviders) {
        List<AiProvider> providers = new ArrayList<>();
        AiProperties.OpenAi openai = properties.getOpenai();
        if (openai.isConfigured()) {
            providers.add(new OpenAiCompatibleProvider("openai", openai.getBaseUrl(), openai.getApiKey(),
                    openai.getModel(), openai.getMaxTokens(), Duration.ofSeconds(openai.getTimeoutSeconds())));
        }
        AiProperties.Claude claude = properties.getClaude();
        if (claude.isConfigured()) {
            providers.add(new ClaudeProvider(claude.getApiKey(), claude.getModel(), claude.getMaxTokens(),
                    claude.getEffort(), claude.isFallbackEnabled()));
        }
        AiProperties.Abacus abacus = properties.getAbacus();
        if (abacus.isConfigured()) {
            providers.add(new OpenAiCompatibleProvider("abacus", abacus.getBaseUrl(), abacus.getApiKey(),
                    abacus.getModel(), abacus.getMaxTokens(), Duration.ofSeconds(abacus.getTimeoutSeconds())));
        }
        // 사용자가 직접 정의한 AiProvider 빈(예: 사내 LLM)도 함께 등록
        customProviders.orderedStream().forEach(providers::add);
        if (providers.isEmpty()) {
            throw new IllegalStateException("No AI provider configured. Set at least one of "
                    + "ai.openai.api-key, ai.claude.api-key, ai.abacus.api-key");
        }
        return new AiClient(providers, properties.getDefaultProvider(), knowledgeBase, parser);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnExpression("'${ai.openai.api-key:}' != ''")
    public OpenAiFineTuneService openAiFineTuneService(AiProperties properties) {
        return new OpenAiFineTuneService(properties.getOpenai().getApiKey(), properties.getOpenai().getBaseUrl());
    }

    /**
     * {@code @SpringBootApplication} 패키지 아래의 {@code @AiService} 를 자동 등록합니다.
     * {@code @EnableAiServices} 로 이미 등록된 빈은 건너뜁니다.
     */
    @Configuration(proxyBeanMethods = false)
    @Import(AiServiceRegistrar.class)
    static class AutoScanConfiguration {
    }
}
