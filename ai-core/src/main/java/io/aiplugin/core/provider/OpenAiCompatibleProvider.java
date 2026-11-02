package io.aiplugin.core.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aiplugin.core.AiException;
import io.aiplugin.core.AiRequest;
import io.aiplugin.core.AiResponse;
import io.aiplugin.core.ResponseFormat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * OpenAI Chat Completions 형식(/v1/chat/completions)을 따르는 서비스용 프로바이더.
 * OpenAI 와 Abacus.AI RouteLLM 이 이 형식을 사용합니다.
 */
public class OpenAiCompatibleProvider implements AiProvider {

    public static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    public static final String ABACUS_BASE_URL = "https://routellm.abacus.ai/v1";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String name;
    private final String baseUrl;
    private final String apiKey;
    private final String defaultModel;
    private final int defaultMaxTokens;
    private final HttpClient http;
    private final Duration timeout;

    public OpenAiCompatibleProvider(String name, String baseUrl, String apiKey, String defaultModel,
                                    int defaultMaxTokens, Duration timeout) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(name + ": API key is required");
        }
        this.name = name;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        this.defaultMaxTokens = defaultMaxTokens;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    public static OpenAiCompatibleProvider openAi(String apiKey, String model) {
        return new OpenAiCompatibleProvider("openai", OPENAI_BASE_URL, apiKey, model, 16000, Duration.ofMinutes(5));
    }

    public static OpenAiCompatibleProvider abacus(String apiKey, String model) {
        return new OpenAiCompatibleProvider("abacus", ABACUS_BASE_URL, apiKey, model, 16000, Duration.ofMinutes(5));
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public AiResponse complete(AiRequest request) {
        String model = request.model() != null ? request.model() : defaultModel;
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("max_completion_tokens", request.maxTokens() != null ? request.maxTokens() : defaultMaxTokens);

        ArrayNode messages = body.putArray("messages");
        String system = FormatInstructions.mergeSystem(request.systemPrompt(), request.format());
        if (system != null) {
            messages.addObject().put("role", "system").put("content", system);
        }
        messages.addObject().put("role", "user").put("content", request.userPrompt());

        if (request.format() != ResponseFormat.TEXT) {
            body.putObject("response_format").put("type", "json_object");
        }

        JsonNode json = post("/chat/completions", body);
        JsonNode choice = json.path("choices").path(0);
        String text = choice.path("message").path("content").asText(null);
        if (text == null) {
            throw new AiException(name + ": empty response (finish_reason=" + choice.path("finish_reason").asText() + ")");
        }
        JsonNode usage = json.path("usage");
        return new AiResponse(text, name, json.path("model").asText(model),
                usage.path("prompt_tokens").asLong(), usage.path("completion_tokens").asLong());
    }

    private JsonNode post(String path, JsonNode body) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new AiException(name + " API error " + res.statusCode() + ": " + res.body());
            }
            return MAPPER.readTree(res.body());
        } catch (IOException e) {
            throw new AiException(name + " request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException(name + " request interrupted", e);
        }
    }
}
