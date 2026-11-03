package io.aiplugin.spring.autoconfigure;

import io.aiplugin.core.local.OllamaClient;
import io.aiplugin.core.rag.RagService;
import io.aiplugin.core.rag.document.DocumentLoader;
import io.aiplugin.core.rag.embedding.EmbeddingModel;
import io.aiplugin.core.rag.embedding.OllamaEmbeddingModel;
import io.aiplugin.core.rag.embedding.OpenAiCompatibleEmbeddingModel;
import io.aiplugin.core.rag.store.FileVectorStore;
import io.aiplugin.core.rag.store.PgVectorStore;
import io.aiplugin.core.rag.store.QdrantVectorStore;
import io.aiplugin.core.rag.store.VectorStore;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Path;

/** {@code ai.rag.enabled=true} 일 때 임베딩 모델, 벡터 DB, {@link RagService} 를 등록합니다. */
@AutoConfiguration(before = AiAutoConfiguration.class)
@EnableConfigurationProperties({RagProperties.class, AiProperties.class})
@ConditionalOnProperty(prefix = "ai.rag", name = "enabled", havingValue = "true")
public class RagAutoConfiguration {

    private static final Log log = LogFactory.getLog(RagAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public EmbeddingModel aiEmbeddingModel(RagProperties rag, AiProperties ai) {
        RagProperties.Embedding e = rag.getEmbedding();
        return switch (e.getProvider()) {
            case "openai" -> {
                String key = e.getApiKey() != null ? e.getApiKey() : ai.getOpenai().getApiKey();
                if (key == null || key.isBlank()) {
                    throw new IllegalStateException("ai.rag.embedding.provider=openai needs ai.openai.api-key "
                            + "or ai.rag.embedding.api-key (or use provider: ollama for free local embeddings)");
                }
                yield new OpenAiCompatibleEmbeddingModel("openai",
                        e.getBaseUrl() != null ? e.getBaseUrl() : ai.getOpenai().getBaseUrl(), key,
                        e.getModel() != null ? e.getModel() : "text-embedding-3-small", e.getBatchSize());
            }
            case "ollama" -> new OllamaEmbeddingModel(new OllamaClient(
                    e.getBaseUrl() != null ? e.getBaseUrl() : ai.getOllama().getBaseUrl()), e.getModel(),
                    Math.min(e.getBatchSize(), 32));
            case "openai-compatible" -> {
                if (e.getBaseUrl() == null || e.getModel() == null) {
                    throw new IllegalStateException("openai-compatible embedding needs ai.rag.embedding.base-url and model");
                }
                yield new OpenAiCompatibleEmbeddingModel(hostName(e.getBaseUrl()), e.getBaseUrl(), e.getApiKey(),
                        e.getModel(), e.getBatchSize());
            }
            default -> throw new IllegalStateException("Unknown ai.rag.embedding.provider: " + e.getProvider());
        };
    }

    private static String hostName(String url) {
        return java.net.URI.create(url).getHost();
    }

    @Bean
    @ConditionalOnMissingBean
    public VectorStore aiVectorStore(RagProperties rag, EmbeddingModel embedding, ObjectProvider<DataSource> dataSource)
            throws IOException {
        RagProperties.Store s = rag.getStore();
        return switch (s.getType()) {
            case "file" -> {
                if (s.getFilePath().startsWith("classpath:")) {
                    Resource r = new PathMatchingResourcePatternResolver().getResource(s.getFilePath());
                    yield FileVectorStore.readOnly(r.getInputStream(), s.getFilePath(), embedding.id());
                }
                yield new FileVectorStore(Path.of(s.getFilePath()), embedding.id());
            }
            case "qdrant" -> new QdrantVectorStore(s.getQdrantUrl(), s.getQdrantCollection(), s.getQdrantApiKey());
            case "pgvector" -> {
                DataSource ds = dataSource.getIfAvailable();
                if (ds == null) {
                    throw new IllegalStateException("ai.rag.store.type=pgvector needs a DataSource "
                            + "(add spring-boot-starter-jdbc + org.postgresql:postgresql and spring.datasource.*)");
                }
                yield new PgVectorStore(ds, s.getPgvectorTable());
            }
            default -> throw new IllegalStateException("Unknown ai.rag.store.type: " + s.getType());
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public RagService ragService(RagProperties rag, EmbeddingModel embedding, VectorStore store) {
        return new RagService(embedding, store, new RagService.Options(rag.getChunkSize(), rag.getChunkOverlap(),
                rag.getTopK(), rag.getMinScore()));
    }

    /** 애플리케이션 시작 후 문서를 색인합니다. 변경되지 않은 문서는 임베딩하지 않습니다. */
    @Bean
    @ConditionalOnProperty(prefix = "ai.rag", name = "ingest-on-startup", havingValue = "true", matchIfMissing = true)
    public ApplicationRunner ragStartupIngest(RagProperties rag, RagService ragService) {
        return args -> {
            if (rag.getStore().getType().equals("file") && rag.getStore().getFilePath().startsWith("classpath:")) {
                return; // 읽기 전용 저장소
            }
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            int docs = 0, chunks = 0, skipped = 0;
            for (String pattern : rag.getDocuments()) {
                String base = basePath(pattern);
                for (Resource r : resolver.getResources(pattern)) {
                    if (!r.isReadable() || r.getFilename() == null || !DocumentLoader.isSupported(r.getFilename())) {
                        continue;
                    }
                    String url = r.getURL().toString();
                    int i = base.isEmpty() ? -1 : url.lastIndexOf(base);
                    String source = i >= 0 ? url.substring(i) : r.getFilename();
                    try {
                        RagService.IngestResult res = ragService.ingest(source, r.getInputStream());
                        docs += res.documents();
                        chunks += res.chunks();
                        skipped += res.skipped();
                    } catch (RuntimeException e) {
                        log.warn("RAG: failed to index " + source + ": " + e.getMessage());
                    }
                }
            }
            log.info("RAG: indexed " + docs + " documents (" + chunks + " chunks), " + skipped
                    + " unchanged; store has " + ragService.store().count() + " chunks");
        };
    }

    /** "classpath*:rag-docs/**" → "rag-docs/" (소스 이름을 jar 위치와 무관하게 만들기 위해). */
    static String basePath(String pattern) {
        String p = pattern.replaceFirst("^(classpath\\*?:|file:)", "");
        int wildcard = p.indexOf('*');
        if (wildcard >= 0) {
            p = p.substring(0, wildcard);
        }
        p = p.replaceFirst("^\\./", "");
        int slash = p.lastIndexOf('/');
        return slash >= 0 ? p.substring(0, slash + 1) : p;
    }
}
