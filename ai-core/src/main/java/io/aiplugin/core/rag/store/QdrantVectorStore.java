package io.aiplugin.core.rag.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aiplugin.core.AiException;
import io.aiplugin.core.rag.Chunk;
import io.aiplugin.core.rag.SearchResult;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Qdrant 벡터 DB (REST API). 컬렉션이 없으면 첫 저장 시 코사인 거리로 생성합니다.
 * 로컬 실행: {@code docker run -p 6333:6333 qdrant/qdrant}
 */
public class QdrantVectorStore implements VectorStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String collection;
    private final String apiKey;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private volatile boolean collectionReady;

    public QdrantVectorStore(String baseUrl, String collection, String apiKey) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.collection = URLEncoder.encode(collection, StandardCharsets.UTF_8);
        this.apiKey = apiKey;
    }

    @Override
    public void upsert(List<Chunk> chunks, List<float[]> vectors) {
        if (chunks.isEmpty()) {
            return;
        }
        ensureCollection(vectors.get(0).length);
        for (int from = 0; from < chunks.size(); from += 128) {
            ObjectNode body = MAPPER.createObjectNode();
            ArrayNode points = body.putArray("points");
            for (int i = from; i < Math.min(chunks.size(), from + 128); i++) {
                Chunk c = chunks.get(i);
                ObjectNode p = points.addObject();
                p.put("id", c.id());
                ArrayNode v = p.putArray("vector");
                for (float f : vectors.get(i)) {
                    v.add(f);
                }
                ObjectNode payload = p.putObject("payload");
                payload.put("source", c.source()).put("index", c.index()).put("content", c.content());
                c.metadata().forEach((k, val) -> payload.put("meta_" + k, val));
            }
            send("PUT", "/collections/" + collection + "/points?wait=true", body);
        }
    }

    @Override
    public List<SearchResult> search(float[] query, int topK, double minScore) {
        if (!exists()) {
            return List.of();
        }
        ObjectNode body = MAPPER.createObjectNode().put("limit", topK).put("with_payload", true)
                .put("score_threshold", minScore);
        ArrayNode v = body.putArray("vector");
        for (float f : query) {
            v.add(f);
        }
        JsonNode res = send("POST", "/collections/" + collection + "/points/search", body);
        List<SearchResult> out = new ArrayList<>();
        for (JsonNode hit : res.path("result")) {
            JsonNode p = hit.path("payload");
            Map<String, String> meta = new HashMap<>();
            p.properties().forEach(e -> {
                if (e.getKey().startsWith("meta_")) {
                    meta.put(e.getKey().substring(5), e.getValue().asText());
                }
            });
            out.add(new SearchResult(new Chunk(hit.path("id").asText(), p.path("source").asText(),
                    p.path("index").asInt(), p.path("content").asText(), meta), hit.path("score").asDouble()));
        }
        return out;
    }

    @Override
    public void deleteSource(String source) {
        if (exists()) {
            send("POST", "/collections/" + collection + "/points/delete?wait=true",
                    MAPPER.createObjectNode().set("filter", filter(Map.of("source", source))));
        }
    }

    @Override
    public boolean containsSource(String source, String hash) {
        if (!exists()) {
            return false;
        }
        ObjectNode body = MAPPER.createObjectNode().put("exact", true);
        body.set("filter", filter(Map.of("source", source, "meta_hash", hash)));
        return send("POST", "/collections/" + collection + "/points/count", body)
                .path("result").path("count").asLong() > 0;
    }

    @Override
    public long count() {
        if (!exists()) {
            return 0;
        }
        return send("POST", "/collections/" + collection + "/points/count",
                MAPPER.createObjectNode().put("exact", true)).path("result").path("count").asLong();
    }

    private static ObjectNode filter(Map<String, String> fields) {
        ObjectNode filter = MAPPER.createObjectNode();
        ArrayNode must = filter.putArray("must");
        fields.forEach((k, v) -> must.addObject().put("key", k).putObject("match").put("value", v));
        return filter;
    }

    private boolean exists() {
        if (collectionReady) {
            return true;
        }
        HttpResponse<String> res = raw("GET", "/collections/" + collection, null);
        if (res.statusCode() == 404) {
            return false;
        }
        check(res);
        collectionReady = true;
        return true;
    }

    private synchronized void ensureCollection(int dimension) {
        if (exists()) {
            return;
        }
        ObjectNode body = MAPPER.createObjectNode();
        body.putObject("vectors").put("size", dimension).put("distance", "Cosine");
        send("PUT", "/collections/" + collection, body);
        // 문서 단위 삭제/중복 확인을 빠르게 하기 위한 payload 인덱스
        send("PUT", "/collections/" + collection + "/index?wait=true",
                MAPPER.createObjectNode().put("field_name", "source").put("field_schema", "keyword"));
        collectionReady = true;
    }

    private JsonNode send(String method, String path, JsonNode body) {
        HttpResponse<String> res = raw(method, path, body);
        check(res);
        try {
            return MAPPER.readTree(res.body());
        } catch (IOException e) {
            throw new AiException("Invalid Qdrant response: " + res.body(), e);
        }
    }

    private static void check(HttpResponse<String> res) {
        if (res.statusCode() / 100 != 2) {
            throw new AiException("Qdrant error " + res.statusCode() + ": " + res.body());
        }
    }

    private HttpResponse<String> raw(String method, String path, JsonNode body) {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofMinutes(1))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body.toString()));
        if (apiKey != null && !apiKey.isBlank()) {
            req.header("api-key", apiKey);
        }
        try {
            return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new AiException("Cannot reach Qdrant at " + baseUrl + ": " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("Qdrant request interrupted", e);
        }
    }
}
