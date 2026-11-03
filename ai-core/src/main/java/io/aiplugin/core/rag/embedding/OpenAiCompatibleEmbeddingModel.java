package io.aiplugin.core.rag.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aiplugin.core.AiException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI {@code /embeddings} 형식 임베딩. OpenAI, Voyage AI(https://api.voyageai.com/v1),
 * llama.cpp({@code llama-server --embedding}) 등에서 사용할 수 있습니다.
 */
public class OpenAiCompatibleEmbeddingModel implements EmbeddingModel {

    public static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    public static final String VOYAGE_BASE_URL = "https://api.voyageai.com/v1";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String name;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int batchSize;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public OpenAiCompatibleEmbeddingModel(String name, String baseUrl, String apiKey, String model, int batchSize) {
        this.name = name;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.batchSize = batchSize;
    }

    public static OpenAiCompatibleEmbeddingModel openAi(String apiKey, String model) {
        return new OpenAiCompatibleEmbeddingModel("openai", OPENAI_BASE_URL, apiKey,
                model == null ? "text-embedding-3-small" : model, 64);
    }

    @Override
    public String id() {
        return name + "/" + model;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> out = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += batchSize) {
            out.addAll(embedBatch(texts.subList(i, Math.min(texts.size(), i + batchSize))));
        }
        return out;
    }

    private List<float[]> embedBatch(List<String> batch) {
        ObjectNode body = MAPPER.createObjectNode().put("model", model);
        batch.forEach(body.putArray("input")::add);
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(baseUrl + "/embeddings"))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (apiKey != null && !apiKey.isBlank()) {
            req.header("Authorization", "Bearer " + apiKey);
        }
        try {
            HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new AiException(name + " embedding error " + res.statusCode() + ": " + res.body());
            }
            JsonNode data = MAPPER.readTree(res.body()).path("data");
            float[][] vectors = new float[batch.size()][];
            for (JsonNode item : data) {
                vectors[item.path("index").asInt()] = Vectors.toFloats(item.path("embedding"));
            }
            return List.of(vectors);
        } catch (IOException e) {
            throw new AiException(name + " embedding request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("embedding interrupted", e);
        }
    }
}
