package io.aiplugin.idea;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import io.aiplugin.core.rag.RagService;
import io.aiplugin.core.rag.embedding.EmbeddingModel;
import io.aiplugin.core.rag.embedding.OllamaEmbeddingModel;
import io.aiplugin.core.rag.embedding.OpenAiCompatibleEmbeddingModel;
import io.aiplugin.core.rag.store.FileVectorStore;
import io.aiplugin.core.rag.store.QdrantVectorStore;
import io.aiplugin.core.rag.store.VectorStore;
import io.aiplugin.idea.settings.AiSettings;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** 프로젝트별 RAG 서비스. RAG 설정이 바뀌면 새로 만듭니다. PasswordSafe 를 읽으므로 백그라운드에서 호출하세요. */
@Service(Service.Level.PROJECT)
public final class RagProjectService {

    private final Project project;
    private RagService rag;
    private List<Object> config;

    public RagProjectService(Project project) {
        this.project = project;
    }

    public static RagProjectService getInstance(Project project) {
        return project.getService(RagProjectService.class);
    }

    public synchronized RagService rag() {
        AiSettings.State s = AiSettings.getInstance().getState();
        List<Object> current = List.of(s.ragEmbeddingProvider, s.ragEmbeddingModel, s.ragEmbeddingBaseUrl,
                s.ragStoreType, s.ragFilePath, s.ragQdrantUrl, s.ragQdrantCollection, s.ragChunkSize, s.ragTopK,
                s.ollamaBaseUrl);
        if (rag == null || !Objects.equals(current, config)) {
            EmbeddingModel embedding = embedding(s);
            rag = new RagService(embedding, store(s, embedding),
                    new RagService.Options(s.ragChunkSize, Math.max(0, s.ragChunkSize / 8), s.ragTopK, 0.3));
            config = current;
        }
        return rag;
    }

    public Path storeFile() {
        Path p = Path.of(AiSettings.getInstance().getState().ragFilePath);
        return p.isAbsolute() ? p : Path.of(project.getBasePath()).resolve(p);
    }

    /** 프로젝트 안의 파일은 프로젝트 기준 상대 경로를 문서 이름으로 씁니다. */
    public String sourceName(Path file) {
        Path base = Path.of(project.getBasePath()).toAbsolutePath();
        Path abs = file.toAbsolutePath();
        return abs.startsWith(base) ? base.relativize(abs).toString().replace('\\', '/') : abs.toString();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static EmbeddingModel embedding(AiSettings.State s) {
        String model = blankToNull(s.ragEmbeddingModel);
        String baseUrl = blankToNull(s.ragEmbeddingBaseUrl);
        return switch (s.ragEmbeddingProvider) {
            case "ollama" -> new OllamaEmbeddingModel(baseUrl != null ? baseUrl : s.ollamaBaseUrl, model);
            case "openai-compatible" -> {
                if (baseUrl == null || model == null) {
                    throw new IllegalStateException("openai-compatible embedding needs a base URL and model");
                }
                yield new OpenAiCompatibleEmbeddingModel(java.net.URI.create(baseUrl).getHost(), baseUrl,
                        AiSettings.getApiKey("embedding"), model, 64);
            }
            default -> {
                String key = AiSettings.getApiKey("openai");
                if (key == null) {
                    throw new IllegalStateException("OpenAI API key is required for openai embeddings "
                            + "(or choose 'ollama' for free local embeddings)");
                }
                yield new OpenAiCompatibleEmbeddingModel("openai",
                        baseUrl != null ? baseUrl : OpenAiCompatibleEmbeddingModel.OPENAI_BASE_URL, key,
                        model != null ? model : "text-embedding-3-small", 64);
            }
        };
    }

    private VectorStore store(AiSettings.State s, EmbeddingModel embedding) {
        if ("qdrant".equals(s.ragStoreType)) {
            return new QdrantVectorStore(s.ragQdrantUrl, s.ragQdrantCollection, null);
        }
        return new FileVectorStore(storeFile(), embedding.id());
    }
}
