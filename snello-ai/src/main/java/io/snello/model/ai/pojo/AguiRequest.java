package io.snello.model.ai.pojo;

import java.util.Set;

/** Agent run request dispatched from the WebSocket via CDI async events. */
public record AguiRequest(
        String connectionId,
        String userId,
        String username,
        Set<String> roles,
        String threadId,
        String runId,
        String message,
        String model,
        String language,
        String projectUuid,
        long enqueuedAtMs) {

    public boolean usePro() {
        return "pro".equalsIgnoreCase(model);
    }

    public boolean useEnglish() {
        return "en".equalsIgnoreCase(language);
    }
}
