package io.aiplugin.core.rag.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aiplugin.core.AiException;
import io.aiplugin.core.rag.Chunk;
import io.aiplugin.core.rag.SearchResult;
import io.aiplugin.core.rag.embedding.Vectors;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 외부 DB 없이 쓰는 파일 기반 벡터 저장소 (메모리 검색 + JSON 저장, 확장자가 .gz 이면 압축).
 * 수만 개 조각까지 충분히 빠릅니다. IntelliJ 에서 만든 저장소 파일을 Spring 앱 classpath 에 넣어 쓸 수 있습니다.
 */
public class FileVectorStore implements VectorStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path path;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private String embeddingModel;

    private record Entry(Chunk chunk, float[] vector) {
    }

    /**
     * @param path           저장 파일 (null 이면 메모리 전용)
     * @param embeddingModel 이 저장소를 만든 임베딩 모델 id. 다른 모델로 만든 파일을 열면 오류를 냅니다.
     */
    public FileVectorStore(Path path, String embeddingModel) {
        this.path = path;
        this.embeddingModel = embeddingModel;
        if (path != null && Files.exists(path)) {
            try (InputStream in = open(path)) {
                read(in);
            } catch (IOException e) {
                throw new AiException("Cannot read vector store " + path + ": " + e.getMessage(), e);
            }
        }
    }

    /** classpath 등에서 읽기 전용으로 엽니다. */
    public static FileVectorStore readOnly(InputStream in, String name, String embeddingModel) {
        FileVectorStore store = new FileVectorStore(null, embeddingModel);
        try (InputStream stream = name.endsWith(".gz") ? new GZIPInputStream(in) : in) {
            store.read(stream);
        } catch (IOException e) {
            throw new AiException("Cannot read vector store " + name + ": " + e.getMessage(), e);
        }
        return store;
    }

    private static InputStream open(Path p) throws IOException {
        InputStream in = Files.newInputStream(p);
        return p.toString().endsWith(".gz") ? new GZIPInputStream(in) : in;
    }

    private void read(InputStream in) throws IOException {
        JsonNode root = MAPPER.readTree(in);
        String fileModel = root.path("embeddingModel").asText(null);
        if (embeddingModel != null && fileModel != null && !fileModel.equals(embeddingModel)) {
            throw new AiException("Vector store was built with embedding model '" + fileModel
                    + "' but '" + embeddingModel + "' is configured. Use the same model or rebuild the store.");
        }
        if (embeddingModel == null) {
            embeddingModel = fileModel;
        }
        for (JsonNode n : root.path("chunks")) {
            Map<String, String> meta = new HashMap<>();
            n.path("metadata").properties().forEach(e -> meta.put(e.getKey(), e.getValue().asText()));
            Chunk chunk = new Chunk(n.path("id").asText(), n.path("source").asText(), n.path("index").asInt(),
                    n.path("content").asText(), meta);
            entries.put(chunk.id(), new Entry(chunk, Vectors.toFloats(n.path("vector"))));
        }
    }

    public String embeddingModel() {
        return embeddingModel;
    }

    @Override
    public synchronized void upsert(List<Chunk> chunks, List<float[]> vectors) {
        for (int i = 0; i < chunks.size(); i++) {
            entries.put(chunks.get(i).id(), new Entry(chunks.get(i), vectors.get(i)));
        }
    }

    @Override
    public synchronized List<SearchResult> search(float[] query, int topK, double minScore) {
        List<SearchResult> results = new ArrayList<>();
        for (Entry e : entries.values()) {
            double score = Vectors.cosine(query, e.vector());
            if (score >= minScore) {
                results.add(new SearchResult(e.chunk(), score));
            }
        }
        results.sort(Comparator.comparingDouble(SearchResult::score).reversed());
        return results.size() > topK ? new ArrayList<>(results.subList(0, topK)) : results;
    }

    @Override
    public synchronized void deleteSource(String source) {
        entries.values().removeIf(e -> e.chunk().source().equals(source));
    }

    @Override
    public synchronized boolean containsSource(String source, String hash) {
        return entries.values().stream().anyMatch(e -> e.chunk().source().equals(source)
                && hash.equals(e.chunk().metadata().get("hash")));
    }

    @Override
    public synchronized long count() {
        return entries.size();
    }

    @Override
    public synchronized Map<String, Long> sources() {
        Map<String, Long> out = new TreeMap<>();
        entries.values().forEach(e -> out.merge(e.chunk().source(), 1L, Long::sum));
        return out;
    }

    /** 임시 파일에 쓴 뒤 교체하므로 저장 중 중단되어도 기존 파일이 깨지지 않습니다. */
    @Override
    public synchronized void flush() {
        if (path == null) {
            return;
        }
        ObjectNode root = MAPPER.createObjectNode();
        root.put("version", 1);
        root.put("embeddingModel", embeddingModel);
        ArrayNode arr = root.putArray("chunks");
        for (Entry e : entries.values()) {
            ObjectNode n = arr.addObject();
            n.put("id", e.chunk().id());
            n.put("source", e.chunk().source());
            n.put("index", e.chunk().index());
            n.put("content", e.chunk().content());
            ObjectNode meta = n.putObject("metadata");
            e.chunk().metadata().forEach(meta::put);
            ArrayNode vec = n.putArray("vector");
            for (float f : e.vector()) {
                vec.add(f);
            }
        }
        try {
            Path parent = path.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Path tmp = Files.createTempFile(parent, ".vector-store", ".tmp");
            try (OutputStream out = path.toString().endsWith(".gz")
                    ? new GZIPOutputStream(Files.newOutputStream(tmp)) : Files.newOutputStream(tmp)) {
                MAPPER.writeValue(out, root);
            }
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new AiException("Cannot write vector store " + path + ": " + e.getMessage(), e);
        }
    }
}
