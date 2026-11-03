package io.aiplugin.core.rag.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aiplugin.core.AiException;
import io.aiplugin.core.local.OllamaClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Ollama 로컬 임베딩 ({@code /api/embed}). 한국어 문서에는 다국어 모델 {@code bge-m3} 를 권장합니다.
 * 모델이 없으면 처음 사용할 때 자동으로 내려받습니다.
 */
public class OllamaEmbeddingModel implements EmbeddingModel {

    public static final String DEFAULT_MODEL = "bge-m3";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OllamaClient client;
    private final String model;
    private final int batchSize;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile boolean ready;

    public OllamaEmbeddingModel(OllamaClient client, String model, int batchSize) {
        this.client = client;
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model;
        this.batchSize = batchSize;
    }

    public OllamaEmbeddingModel(String baseUrl, String model) {
        this(new OllamaClient(baseUrl), model, 32);
    }

    @Override
    public String id() {
        return "ollama/" + model;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (!ready) {
            if (!client.hasModel(model)) {
                client.pull(model, null);
            }
            ready = true;
        }
        List<float[]> out = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += batchSize) {
            List<String> batch = texts.subList(i, Math.min(texts.size(), i + batchSize));
            ObjectNode body = MAPPER.createObjectNode().put("model", model);
            batch.forEach(body.putArray("input")::add);
            try {
                HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(client.baseUrl() + "/api/embed"))
                        .timeout(Duration.ofMinutes(5))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),
                        HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() / 100 != 2) {
                    throw new AiException("ollama embedding error " + res.statusCode() + ": " + res.body());
                }
                for (JsonNode v : MAPPER.readTree(res.body()).path("embeddings")) {
                    out.add(Vectors.toFloats(v));
                }
            } catch (IOException e) {
                throw new AiException("Cannot reach Ollama at " + client.baseUrl() + ": " + e, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiException("embedding interrupted", e);
            }
        }
        if (out.size() != texts.size()) {
            throw new AiException("ollama returned " + out.size() + " embeddings for " + texts.size() + " inputs");
        }
        return out;
    }
}
