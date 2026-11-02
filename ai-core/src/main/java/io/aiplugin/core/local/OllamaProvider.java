package io.aiplugin.core.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aiplugin.core.AiException;
import io.aiplugin.core.AiRequest;
import io.aiplugin.core.AiResponse;
import io.aiplugin.core.ResponseFormat;
import io.aiplugin.core.provider.AiProvider;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Ollama 로 로컬 모델을 실행하는 프로바이더. {@code autoPull=true} 이면 모델이 없을 때 처음 호출에서 내려받습니다.
 */
public class OllamaProvider implements AiProvider {

    private final OllamaClient client;
    private final String defaultModel;
    private final int defaultMaxTokens;
    private final boolean autoPull;
    private final Duration timeout;
    private final Consumer<DownloadProgress> pullListener;
    private final Set<String> readyModels = ConcurrentHashMap.newKeySet();

    public OllamaProvider(OllamaClient client, String defaultModel, int defaultMaxTokens, boolean autoPull,
                          Duration timeout, Consumer<DownloadProgress> pullListener) {
        this.client = client;
        this.defaultModel = defaultModel;
        this.defaultMaxTokens = defaultMaxTokens;
        this.autoPull = autoPull;
        this.timeout = timeout;
        this.pullListener = pullListener;
    }

    public OllamaProvider(String baseUrl, String model) {
        this(new OllamaClient(baseUrl), model, 4096, true, Duration.ofMinutes(10), null);
    }

    public OllamaClient client() {
        return client;
    }

    @Override
    public String name() {
        return "ollama";
    }

    @Override
    public AiResponse complete(AiRequest request) {
        String model = request.model() != null ? request.model() : defaultModel;
        ensureModel(model);

        ObjectNode body = OllamaClient.MAPPER.createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        body.putObject("options").put("num_predict",
                request.maxTokens() != null ? request.maxTokens() : defaultMaxTokens);
        if (request.format() != ResponseFormat.TEXT) {
            body.put("format", "json");
        }
        ArrayNode messages = body.putArray("messages");
        String system = LocalFormat.mergeSystem(request.systemPrompt(), request.format());
        if (system != null) {
            messages.addObject().put("role", "system").put("content", system);
        }
        messages.addObject().put("role", "user").put("content", request.userPrompt());

        JsonNode json = client.sendJson(HttpRequest.newBuilder(URI.create(client.baseUrl() + "/api/chat"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())));
        String text = json.path("message").path("content").asText(null);
        if (text == null) {
            throw new AiException("ollama: empty response " + json);
        }
        return new AiResponse(text, name(), json.path("model").asText(model),
                json.path("prompt_eval_count").asLong(), json.path("eval_count").asLong());
    }

    private void ensureModel(String model) {
        if (readyModels.contains(model)) {
            return;
        }
        if (!client.hasModel(model)) {
            if (!autoPull) {
                throw new AiException("ollama model '" + model + "' is not installed. Run: ollama pull " + model);
            }
            client.pull(model, pullListener);
        }
        readyModels.add(model);
    }
}
