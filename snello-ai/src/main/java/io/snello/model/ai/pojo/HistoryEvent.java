package io.snello.model.ai.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HistoryEvent(
        String datetime,
        Channel channel,
        @JsonProperty("user_id") String userId,
        String username,
        Type type,
        String body,
        @JsonProperty("session_id") String sessionId,
        String model) {

    public static HistoryEvent of(Channel channel, String userId, String username, Type type, String body,
            String sessionId) {
        return of(channel, userId, username, type, body, sessionId, null);
    }

    public static HistoryEvent of(Channel channel, String userId, String username, Type type, String body,
            String sessionId, String model) {
        return new HistoryEvent(Instant.now().toString(), channel, userId, username, type, body, sessionId, model);
    }

    public enum Channel {
        AGUI("agui");
        private final String value;
        Channel(String value) { this.value = value; }
        @JsonValue
        public String value() { return value; }
    }

    public enum Type {
        LOGIN("login"), LOGOUT("logout"), REQUEST("request"), RESPONSE("response"),
        ERROR("error"), FEEDBACK("feedback"), TOOL("tool");
        private final String value;
        Type(String value) { this.value = value; }
        @JsonValue
        public String value() { return value; }
    }
}
