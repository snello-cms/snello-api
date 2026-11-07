package io.snello.service.ai.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Thumbs up/down on an assistant message.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FeedbackRequest(
        String vote,
        String sessionId,
        String threadId,
        String messageId,
        String assistantBody,
        String model) {
}
