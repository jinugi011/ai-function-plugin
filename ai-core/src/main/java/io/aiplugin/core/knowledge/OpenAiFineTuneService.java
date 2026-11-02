package io.aiplugin.core.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aiplugin.core.AiException;
import io.aiplugin.core.provider.OpenAiCompatibleProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * OpenAI 파인튜닝 API 연동: 학습 파일 업로드 → 파인튜닝 작업 생성 → 상태 조회.
 * 작업이 끝나면 결과 모델 ID(ft:...)를 openai 프로바이더의 model 로 지정해 사용합니다.
 */
public class OpenAiFineTuneService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public OpenAiFineTuneService(String apiKey) {
        this(apiKey, OpenAiCompatibleProvider.OPENAI_BASE_URL);
    }

    public OpenAiFineTuneService(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    /** 예제를 업로드하고 파인튜닝 작업을 시작합니다. 반환값은 작업 정보(JSON, id/status 포함). */
    public JsonNode startFineTune(List<TrainingExample> examples, String systemPrompt, String baseModel, String suffix) {
        if (examples.size() < 10) {
            throw new AiException("OpenAI fine-tuning requires at least 10 examples (got " + examples.size() + ")");
        }
        String fileId = uploadTrainingFile(FineTuneExporter.toOpenAiJsonl(examples, systemPrompt));
        var body = MAPPER.createObjectNode().put("training_file", fileId).put("model", baseModel);
        if (suffix != null && !suffix.isBlank()) {
            body.put("suffix", suffix);
        }
        return send(jsonRequest("/fine_tuning/jobs").POST(HttpRequest.BodyPublishers.ofString(body.toString())));
    }

    public JsonNode getJob(String jobId) {
        return send(baseRequest("/fine_tuning/jobs/" + jobId).GET());
    }

    public String uploadTrainingFile(String jsonl) {
        String boundary = "----aiplugin" + UUID.randomUUID();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writePart(out, boundary, "Content-Disposition: form-data; name=\"purpose\"\r\n\r\nfine-tune\r\n");
        writePart(out, boundary, "Content-Disposition: form-data; name=\"file\"; filename=\"training.jsonl\"\r\n"
                + "Content-Type: application/jsonl\r\n\r\n" + jsonl + "\r\n");
        out.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        JsonNode res = send(baseRequest("/files")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())));
        return res.path("id").asText();
    }

    private static void writePart(ByteArrayOutputStream out, String boundary, String part) {
        out.writeBytes(("--" + boundary + "\r\n" + part).getBytes(StandardCharsets.UTF_8));
    }

    private HttpRequest.Builder baseRequest(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofMinutes(2))
                .header("Authorization", "Bearer " + apiKey);
    }

    private HttpRequest.Builder jsonRequest(String path) {
        return baseRequest(path).header("Content-Type", "application/json");
    }

    private JsonNode send(HttpRequest.Builder req) {
        try {
            HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new AiException("OpenAI fine-tune API error " + res.statusCode() + ": " + res.body());
            }
            return MAPPER.readTree(res.body());
        } catch (IOException e) {
            throw new AiException("OpenAI fine-tune request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("OpenAI fine-tune request interrupted", e);
        }
    }
}
