package io.snello.test.ai;

import dev.langchain4j.exception.InvalidRequestException;
import io.snello.service.ai.agent.AguiAgentWorker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rejected provider key must be reported as a configuration problem, not as "start a new conversation":
 * Gemini answers a bad key with a 400 INVALID_ARGUMENT that langchain4j maps to InvalidRequestException.
 */
class AguiApiKeyErrorTest {

    @Test
    void geminiInvalidKeyIsRecognised() {
        String body = "{\"error\":{\"code\":400,\"message\":\"API key not valid. Please pass a valid API key.\","
                + "\"status\":\"INVALID_ARGUMENT\",\"details\":[{\"reason\":\"API_KEY_INVALID\"}]}}";
        assertTrue(AguiAgentWorker.isApiKeyRejected(new InvalidRequestException(body)));
    }

    @Test
    void otherInvalidRequestsAreNotKeyErrors() {
        assertFalse(AguiAgentWorker.isApiKeyRejected(
                new InvalidRequestException("function call turn must come after a user turn")));
        assertFalse(AguiAgentWorker.isApiKeyRejected(new InvalidRequestException((String) null)));
    }
}
