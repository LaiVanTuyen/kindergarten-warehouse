package com.kindergarten.warehouse.service.ai;

/**
 * The only place that talks to the model. Kept as an interface so the assistant's
 * validation logic is tested without network calls or an API key.
 */
public interface ClaudeGateway {

    /** False when no API key is configured; callers must return AI_DISABLED. */
    boolean isEnabled();

    /**
     * One request with structured output: the reply is parsed into {@code type}.
     *
     * @throws com.kindergarten.warehouse.exception.AppException AI_REFUSED / AI_UNAVAILABLE
     */
    <T> T structured(String system, String user, Class<T> type);
}
