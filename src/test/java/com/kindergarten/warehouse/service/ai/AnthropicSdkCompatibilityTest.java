package com.kindergarten.warehouse.service.ai;

import com.anthropic.models.messages.MessageCreateParams;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Boot 3.2 pins Jackson/Kotlin/OkHttp versions below what the Anthropic SDK was
 * built with. This builds real structured-output requests (schema derived from our records)
 * and a client, without any network call, so an incompatibility fails here and not in a
 * teacher's browser.
 */
class AnthropicSdkCompatibilityTest {

    @Test
    void structuredOutputSchemasBuildWithTheProjectsJacksonVersion() {
        for (Class<?> type : new Class<?>[] {
                AiSchemas.SearchIntent.class, AiSchemas.Ranking.class, AiSchemas.PlanDraft.class }) {
            var params = MessageCreateParams.builder()
                    .model("claude-opus-5-5")
                    .maxTokens(16000L)
                    .system("s")
                    .outputConfig(type)
                    .addUserMessage("u")
                    .build();
            assertThat(params.rawParams().outputConfig()).isPresent();
        }
    }

    @Test
    void gatewayWithKeyIsEnabledAndWithoutKeyIsDisabled() {
        assertThat(new AnthropicClaudeGateway("sk-ant-test", "claude-opus-5-5").isEnabled()).isTrue();
        assertThat(new AnthropicClaudeGateway("", "claude-opus-5-5").isEnabled()).isFalse();
    }
}
