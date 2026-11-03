package io.aiplugin.core.rag;

import io.aiplugin.core.ContextRetriever;
import io.aiplugin.core.rag.document.DocumentLoader;
import io.aiplugin.core.rag.document.LoadedDocument;
import io.aiplugin.core.rag.document.TextSplitter;
import io.aiplugin.core.rag.embedding.EmbeddingModel;
import io.aiplugin.core.rag.store.VectorStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * RAG 파이프라인: 문서 읽기 → 청크 분할 → 임베딩 → 벡터 DB 저장, 그리고 질의 시 유사 조각 검색.
 *
 * <pre>{@code
 * RagService rag = new RagService(new OllamaEmbeddingModel(null, "bge-m3"),
 *         new FileVectorStore(Path.of("vector-store.json"), "ollama/bge-m3"), RagService.Options.defaults());
 * rag.ingest(Path.of("docs"));                       // PDF, TXT, MD
 * List<SearchResult> hits = rag.search("환불 규정", 4);
 * AiClient client = new AiClient(providers, "claude", null, null, rag);  // 프롬프트에 자동 주입
 * }</pre>
 */
public class RagService implements ContextRetriever {

    /**
     * @param chunkSize    조각 최대 글자 수
     * @param chunkOverlap 조각 간 겹침 글자 수
     * @param topK         검색해서 프롬프트에 넣을 조각 수
     * @param minScore     이 유사도 미만인 조각은 버림 (0~1)
     */
    public record Options(int chunkSize, int chunkOverlap, int topK, double minScore) {
        public static Options defaults() {
            return new Options(800, 100, 4, 0.3);
        }
    }

    /** 색인 결과. skipped 는 내용이 바뀌지 않아 건너뛴 문서 수. */
    public record IngestResult(int documents, int chunks, int skipped, List<String> errors) {
        IngestResult merge(IngestResult o) {
            List<String> all = new ArrayList<>(errors);
            all.addAll(o.errors);
            return new IngestResult(documents + o.documents, chunks + o.chunks, skipped + o.skipped, all);
        }

        static IngestResult empty() {
            return new IngestResult(0, 0, 0, List.of());
        }
    }

    private final EmbeddingModel embedding;
    private final VectorStore store;
    private final Options options;
    private final TextSplitter splitter;

    public RagService(EmbeddingModel embedding, VectorStore store, Options options) {
        this.embedding = embedding;
        this.store = store;
        this.options = options;
        this.splitter = new TextSplitter(options.chunkSize(), options.chunkOverlap());
    }

    public VectorStore store() {
        return store;
    }

    public EmbeddingModel embedding() {
        return embedding;
    }

    /** 파일 하나 또는 폴더(하위 포함)의 PDF/TXT/MD 문서를 색인합니다. */
    public IngestResult ingest(Path path) {
        return ingest(path, null);
    }

    public IngestResult ingest(Path path, Consumer<String> progress) {
        List<Path> files;
        if (Files.isDirectory(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                files = walk.filter(Files::isRegularFile)
                        .filter(p -> DocumentLoader.isSupported(p.getFileName().toString()))
                        .sorted().toList();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            files = List.of(path);
        }
        IngestResult total = IngestResult.empty();
        for (Path file : files) {
            if (progress != null) {
                progress.accept("Indexing " + file.getFileName());
            }
            try {
                total = total.merge(ingest(DocumentLoader.load(file)));
            } catch (RuntimeException e) {
                total = total.merge(new IngestResult(0, 0, 0, List.of(file + ": " + e.getMessage())));
            }
        }
        store.flush();
        return total;
    }

    public IngestResult ingest(String source, InputStream in) {
        IngestResult r = ingest(DocumentLoader.load(source, in));
        store.flush();
        return r;
    }

    /** DB 레코드, API 응답 등 파일이 아닌 텍스트를 색인합니다. */
    public IngestResult ingestText(String source, String text, Map<String, String> metadata) {
        LoadedDocument doc = new LoadedDocument(source, List.of(text),
                DocumentLoader.sha256(text.getBytes(StandardCharsets.UTF_8)));
        IngestResult r = ingest(doc, metadata);
        store.flush();
        return r;
    }

    public IngestResult ingest(LoadedDocument doc) {
        return ingest(doc, Map.of());
    }

    private IngestResult ingest(LoadedDocument doc, Map<String, String> extraMeta) {
        if (store.containsSource(doc.source(), doc.hash())) {
            return new IngestResult(0, 0, 1, List.of());
        }
        List<Chunk> chunks = new ArrayList<>();
        for (int page = 0; page < doc.pages().size(); page++) {
            for (String text : splitter.split(doc.pages().get(page))) {
                int index = chunks.size();
                Map<String, String> meta = new HashMap<>(extraMeta);
                meta.put("hash", doc.hash());
                if (doc.pages().size() > 1) {
                    meta.put("page", String.valueOf(page + 1));
                }
                String id = UUID.nameUUIDFromBytes((doc.source() + "#" + index).getBytes(StandardCharsets.UTF_8)).toString();
                chunks.add(new Chunk(id, doc.source(), index, text, meta));
            }
        }
        // 내용이 바뀐 문서는 기존 조각을 지우고 다시 저장
        store.deleteSource(doc.source());
        if (!chunks.isEmpty()) {
            store.upsert(chunks, embedding.embed(chunks.stream().map(Chunk::content).toList()));
        }
        return new IngestResult(1, chunks.size(), 0, List.of());
    }

    public void remove(String source) {
        store.deleteSource(source);
        store.flush();
    }

    public List<SearchResult> search(String query, int topK) {
        if (store.count() == 0) {
            return List.of();
        }
        return store.search(embedding.embed(query), topK, options.minScore());
    }

    @Override
    public String retrieveContext(String query) {
        List<SearchResult> hits = search(query, options.topK());
        if (hits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Answer using the following retrieved documents when relevant. "
                + "If they do not contain the answer, say so instead of guessing.\n");
        for (SearchResult hit : hits) {
            Chunk c = hit.chunk();
            sb.append("<document source=\"").append(c.source()).append('"');
            if (c.metadata().containsKey("page")) {
                sb.append(" page=\"").append(c.metadata().get("page")).append('"');
            }
            sb.append(">\n").append(c.content()).append("\n</document>\n");
        }
        return sb.toString().strip();
    }
}
