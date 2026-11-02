package io.aiplugin.core.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aiplugin.core.AiException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Ollama 서버(기본 http://localhost:11434) 관리 API: 모델 목록, 다운로드(pull), 삭제.
 * Ollama 설치: https://ollama.com/download
 */
public class OllamaClient {

    public static final String DEFAULT_BASE_URL = "http://localhost:11434";
    static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public OllamaClient(String baseUrl) {
        String url = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl;
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public boolean isRunning() {
        try {
            HttpResponse<Void> res = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/version"))
                    .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.discarding());
            return res.statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** 로컬에 설치된 모델 이름 목록 (예: "llama3.2:latest"). */
    public List<String> listModels() {
        JsonNode json = sendJson(HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags"))
                .timeout(Duration.ofSeconds(10)).GET());
        List<String> names = new ArrayList<>();
        json.path("models").forEach(m -> names.add(m.path("name").asText()));
        return names;
    }

    /** "llama3.2" 처럼 태그 없이 지정하면 ":latest" 와 비교합니다. */
    public boolean hasModel(String model) {
        String wanted = model.contains(":") ? model : model + ":latest";
        return listModels().stream().anyMatch(n -> n.equals(model) || n.equals(wanted));
    }

    /** 모델을 다운로드합니다. 진행 상황은 스트리밍으로 전달됩니다. 이미 있으면 바로 끝납니다. */
    public void pull(String model, Consumer<DownloadProgress> progress) {
        String body = MAPPER.createObjectNode().put("model", model).put("stream", true).toString();
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/pull"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(res.body(), StandardCharsets.UTF_8))) {
                if (res.statusCode() / 100 != 2) {
                    throw new AiException("ollama pull failed " + res.statusCode() + ": " + readAll(reader));
                }
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    JsonNode event = MAPPER.readTree(line);
                    if (event.has("error")) {
                        throw new AiException("ollama pull failed: " + event.path("error").asText());
                    }
                    if (progress != null) {
                        progress.accept(new DownloadProgress(event.path("status").asText(),
                                event.path("completed").asLong(), event.path("total").asLong()));
                    }
                }
            }
        } catch (IOException e) {
            throw new AiException("Cannot reach Ollama at " + baseUrl + " (is it running? install from https://ollama.com): " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("ollama pull interrupted", e);
        }
    }

    public void delete(String model) {
        sendJson(HttpRequest.newBuilder(URI.create(baseUrl + "/api/delete"))
                .header("Content-Type", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(
                        MAPPER.createObjectNode().put("model", model).toString())));
    }

    JsonNode sendJson(HttpRequest.Builder req) {
        try {
            HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new AiException("ollama API error " + res.statusCode() + ": " + res.body());
            }
            return res.body().isBlank() ? MAPPER.createObjectNode() : MAPPER.readTree(res.body());
        } catch (IOException e) {
            throw new AiException("Cannot reach Ollama at " + baseUrl + " (is it running? install from https://ollama.com): " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("ollama request interrupted", e);
        }
    }

    private static String readAll(BufferedReader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }
}
