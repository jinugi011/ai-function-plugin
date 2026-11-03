package io.aiplugin.spring.autoconfigure;

import io.aiplugin.core.AiClient;
import io.aiplugin.core.ContextRetriever;
import io.aiplugin.core.knowledge.KnowledgeBase;
import io.aiplugin.core.knowledge.OpenAiFineTuneService;
import io.aiplugin.core.local.LlamaCppProvider;
import io.aiplugin.core.local.OllamaClient;
import io.aiplugin.core.local.OllamaProvider;
import io.aiplugin.core.parse.ResponseParser;
import io.aiplugin.core.provider.AiProvider;
import io.aiplugin.core.provider.ClaudeProvider;
import io.aiplugin.core.provider.OpenAiCompatibleProvider;
import io.aiplugin.spring.proxy.AiServiceRegistrar;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(AiProperties.class)
public class AiAutoConfiguration {

    private static final Log log = LogFactory.getLog(AiAutoConfiguration.class);

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
                             ObjectProvider<AiProvider> customProviders, ObjectProvider<ContextRetriever> retriever) {
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
        // ollama / llamacpp 프로바이더 빈과 사용자가 직접 정의한 AiProvider 빈(예: 사내 LLM)도 함께 등록
        customProviders.orderedStream().forEach(providers::add);
        if (providers.isEmpty()) {
            throw new IllegalStateException("No AI provider configured. Set at least one of "
                    + "ai.openai.api-key, ai.claude.api-key, ai.abacus.api-key, ai.ollama.enabled, ai.llamacpp.enabled");
        }
        return new AiClient(providers, properties.getDefaultProvider(), knowledgeBase, parser, retriever.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ai.ollama", name = "enabled", havingValue = "true")
    public OllamaProvider ollamaProvider(AiProperties properties) {
        AiProperties.Ollama o = properties.getOllama();
        OllamaProvider provider = new OllamaProvider(new OllamaClient(o.getBaseUrl()), o.getModel(), o.getMaxTokens(),
                o.isAutoPull(), Duration.ofSeconds(o.getTimeoutSeconds()), progress -> log.info("ollama: " + progress));
        if (o.isPullOnStartup() && !provider.client().hasModel(o.getModel())) {
            log.info("Pulling Ollama model " + o.getModel() + " ...");
            provider.client().pull(o.getModel(), progress -> log.info("ollama: " + progress));
        }
        return provider;
    }

    /** 컨텍스트 종료 시 close() 로 llama-server 프로세스도 함께 종료됩니다. */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ai.llamacpp", name = "enabled", havingValue = "true")
    public LlamaCppProvider llamaCppProvider(AiProperties properties) {
        AiProperties.LlamaCpp l = properties.getLlamacpp();
        LlamaCppProvider provider = LlamaCppProvider.builder()
                .model(l.getModel())
                .modelDir(Path.of(l.getModelDir()))
                .baseUrl(l.getBaseUrl())
                .serverBinary(l.getServerBinary())
                .port(l.getPort())
                .contextSize(l.getContextSize())
                .gpuLayers(l.getGpuLayers())
                .extraArgs(l.getExtraArgs())
                .maxTokens(l.getMaxTokens())
                .startupTimeout(Duration.ofSeconds(l.getStartupTimeoutSeconds()))
                .huggingFaceToken(l.getHuggingFaceToken())
                .progressListener(progress -> log.info("llama.cpp: " + progress))
                .build();
        if (l.isStartOnStartup()) {
            provider.prepare();
        }
        return provider;
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
