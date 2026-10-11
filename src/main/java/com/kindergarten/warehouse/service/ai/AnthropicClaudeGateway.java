package com.kindergarten.warehouse.service.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Claude via the official Java SDK, structured output into the {@link AiSchemas} records. */
@Slf4j
@Component
public class AnthropicClaudeGateway implements ClaudeGateway {

    private final AnthropicClient client;
    private final String model;

    public AnthropicClaudeGateway(
            @Value("${ai.anthropic.api-key:}") String apiKey,
            @Value("${ai.anthropic.model:claude-opus-5-5}") String model) {
        this.model = model;
        this.client = apiKey == null || apiKey.isBlank() ? null
                : AnthropicOkHttpClient.builder()
                        .apiKey(apiKey)
                        // A teacher is waiting on the page: fail fast rather than hang.
                        .timeout(Duration.ofSeconds(90))
                        .maxRetries(2)
                        .build();
        if (client == null) {
            log.info("AI assistant disabled: ai.anthropic.api-key (ANTHROPIC_API_KEY) is not set");
        }
    }

    @Override
    public boolean isEnabled() {
        return client != null;
    }

    @Override
    public <T> T structured(String system, String user, Class<T> type) {
        if (client == null) {
            throw new AppException(ErrorCode.AI_DISABLED);
        }
        StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(system)
                .outputConfig(type)
                .addUserMessage(user)
                .build();
        try {
            var response = client.messages().create(params);
            if (response.stopReason().map(StopReason.REFUSAL::equals).orElse(false)) {
                throw new AppException(ErrorCode.AI_REFUSED);
            }
            if (response.stopReason().map(StopReason.MAX_TOKENS::equals).orElse(false)) {
                // Truncated JSON would not parse into the schema; treat as a failed attempt.
                throw new AppException(ErrorCode.AI_UNAVAILABLE);
            }
            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(text -> text.text())
                    .findFirst()
                    .orElseThrow(() -> new AppException(ErrorCode.AI_UNAVAILABLE));
        } catch (AppException e) {
            throw e;
        } catch (RateLimitException e) {
            log.warn("Claude rate limited: {}", e.getMessage());
            throw new AppException(ErrorCode.AI_UNAVAILABLE);
        } catch (AnthropicServiceException e) {
            log.error("Claude API error {}: {}", e.statusCode(), e.getMessage());
            throw new AppException(ErrorCode.AI_UNAVAILABLE);
        } catch (RuntimeException e) {
            // Network failures and schema-parse errors.
            log.error("Claude call failed: {}", e.toString());
            throw new AppException(ErrorCode.AI_UNAVAILABLE);
        }
    }
}
