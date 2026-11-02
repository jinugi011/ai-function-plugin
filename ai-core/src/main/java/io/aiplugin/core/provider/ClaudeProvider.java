package io.aiplugin.core.provider;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlockParam;
import com.anthropic.models.beta.messages.MessageCreateParams;
import io.aiplugin.core.AiException;
import io.aiplugin.core.AiRequest;
import io.aiplugin.core.AiResponse;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Anthropic Claude 프로바이더 (공식 anthropic-java SDK 사용).
 *
 * <p>안전 분류기가 요청을 거절(refusal)하면 서버 측 fallback 으로 다른 모델이 응답하도록
 * {@code fallbacks: "default"} 를 기본으로 켭니다. {@code fallbackEnabled=false} 로 끌 수 있습니다.
 */
public class ClaudeProvider implements AiProvider {

    public static final String DEFAULT_MODEL = "claude-opus-5-5";
    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final AnthropicClient client;
    private final String defaultModel;
    private final int defaultMaxTokens;
    private final BetaOutputConfig.Effort effort;
    private final boolean fallbackEnabled;

    public ClaudeProvider(String apiKey, String defaultModel, int defaultMaxTokens, String effort, boolean fallbackEnabled) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("claude: API key is required");
        }
        this.client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        this.defaultModel = defaultModel != null ? defaultModel : DEFAULT_MODEL;
        this.defaultMaxTokens = defaultMaxTokens;
        this.effort = effort == null || effort.isBlank() ? null : BetaOutputConfig.Effort.of(effort.toLowerCase());
        this.fallbackEnabled = fallbackEnabled;
    }

    public ClaudeProvider(String apiKey) {
        this(apiKey, DEFAULT_MODEL, 16000, "medium", true);
    }

    @Override
    public String name() {
        return "claude";
    }

    @Override
    public AiResponse complete(AiRequest request) {
        String model = request.model() != null ? request.model() : defaultModel;
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(request.maxTokens() != null ? request.maxTokens() : defaultMaxTokens)
                .addUserMessage(request.userPrompt());

        String system = FormatInstructions.mergeSystem(request.systemPrompt(), request.format());
        if (system != null) {
            // 시스템 프롬프트(지식베이스 포함)는 요청마다 반복되므로 프롬프트 캐시 대상으로 지정
            params.systemOfBetaTextBlockParams(List.of(BetaTextBlockParam.builder()
                    .text(system)
                    .cacheControl(BetaCacheControlEphemeral.builder().build())
                    .build()));
        }
        if (effort != null) {
            params.outputConfig(BetaOutputConfig.builder().effort(effort).build());
        }
        if (fallbackEnabled) {
            params.addBeta(FALLBACK_BETA);
            params.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }

        BetaMessage message;
        try {
            message = client.beta().messages().create(params.build());
        } catch (RuntimeException e) {
            throw new AiException("claude request failed: " + e.getMessage(), e);
        }

        if (message.stopReason().map(r -> r.equals(BetaStopReason.REFUSAL)).orElse(false)) {
            throw new AiException("claude refused the request" + message.stopDetails()
                    .map(d -> " (" + d.category().map(Object::toString).orElse("unknown") + ")").orElse(""));
        }

        String text = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text())
                .collect(Collectors.joining());
        if (message.stopReason().map(r -> r.equals(BetaStopReason.MAX_TOKENS)).orElse(false)) {
            throw new AiException("claude response truncated at max_tokens; increase maxTokens");
        }
        return new AiResponse(text, name(), message.model().asString(),
                message.usage().inputTokens(), message.usage().outputTokens());
    }
}
