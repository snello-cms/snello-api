package io.snello.service.ai.wss;

import io.quarkiverse.langchain4j.ChatMemoryRemover;
import io.quarkus.logging.Log;
import io.quarkus.oidc.BearerTokenAuthentication;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnError;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.UserData.TypedKey;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Uni;
import io.snello.service.ai.agent.AgentService;
import io.snello.service.ai.agent.ProAgentService;
import io.snello.service.ai.history.HistoryEventPublisher;
import io.snello.model.ai.pojo.AguiRequest;
import io.snello.model.ai.pojo.AguiResponse;
import io.snello.model.ai.pojo.HistoryEvent;
import io.snello.model.ai.pojo.ToolCallTelemetry;
import io.snello.service.ai.skills.ProjectAccess;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@BearerTokenAuthentication
@WebSocket(path = "/agui")
@ApplicationScoped
public class AguiServiceWss {
    private static final TypedKey<String> USER_ID = TypedKey.forString("userId");
    private static final TypedKey<String> USERNAME = TypedKey.forString("username");

    private final Map<String, WebSocketConnection> connections = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> memories = new ConcurrentHashMap<>();

    @Inject
    JsonMapper mapper;

    @Inject
    SecurityIdentity identity;

    @Inject
    Event<AguiRequest> requests;

    @Inject
    AgentService agentService;

    @Inject
    ProAgentService proAgentService;

    @Inject
    HistoryEventPublisher history;

    @Inject
    ProjectAccess projectAccess;

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        Principal principal = identity.getPrincipal();
        String userId = subject(principal);
        String username = readableName(principal);
        connection.userData().put(USER_ID, userId);
        connection.userData().put(USERNAME, username);

        connections.put(connection.id(), connection);
        history.login(HistoryEvent.Channel.AGUI, userId, username, connection.id());
        Log.infof("AG-UI connected: %s session=%s", username, connection.id());

        emit(connection.id(), event("SESSION_STARTED")
                .put("sessionId", connection.id())
                .put("username", username));
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        forget(connection);
        Log.infof("AG-UI disconnected: %s", connection.id());
    }

    @OnError
    public void onError(WebSocketConnection connection, Throwable throwable) {
        forget(connection);
        Log.error("AG-UI connection failed: " + connection.id(), throwable);
    }

    @OnTextMessage
    public void onMessage(String payload, WebSocketConnection connection) {
        String connectionId = connection.id();
        String userId = connection.userData().get(USER_ID);
        String username = connection.userData().get(USERNAME);
        String threadId = connectionId;
        String runId = UUID.randomUUID().toString();
        try {
            JsonNode input = mapper.readTree(payload);
            threadId = text(input, "threadId", connectionId);
            runId = text(input, "runId", runId);

            String message = lastUserMessage(input);
            if (message == null) {
                emitRunError(connectionId, "RunAgentInput carries no user message");
                return;
            }
            String model = modelMode(input);
            String language = replyLanguage(input);
            String projectUuid = projectUuid(input);
            if (projectUuid == null) {
                emitRunError(connectionId, "Missing or unauthorized projectUuid in forwardedProps");
                return;
            }
            history.request(HistoryEvent.Channel.AGUI, userId, username, message, connectionId, model);

            memories.computeIfAbsent(connectionId, key -> ConcurrentHashMap.newKeySet())
                    .add(memoryId(userId, threadId));
            emit(connectionId, event("RUN_STARTED").put("threadId", threadId).put("runId", runId));
            requests.fireAsync(new AguiRequest(connectionId, userId, username, identity.getRoles(),
                    threadId, runId, message, model, language, projectUuid, System.currentTimeMillis()));
        } catch (Exception failure) {
            Log.error("Invalid AG-UI input on session: " + connectionId, failure);
            emitRunError(connectionId, "Invalid RunAgentInput: " + failure.getMessage());
        }
    }

    void onToolCall(@Observes ToolCallTelemetry telemetry) {
        if (telemetry.connectionId() == null || telemetry.connectionId().isBlank()) {
            return;
        }
        ObjectNode ev = switch (telemetry.phase()) {
            case START -> event("TOOL_CALL_START")
                    .put("toolCallId", telemetry.toolCallId())
                    .put("toolCallName", telemetry.toolCallName());
            case ARGS -> event("TOOL_CALL_ARGS")
                    .put("toolCallId", telemetry.toolCallId())
                    .put("delta", telemetry.payload() == null ? "{}" : telemetry.payload());
            case END -> event("TOOL_CALL_END")
                    .put("toolCallId", telemetry.toolCallId());
            case RESULT -> {
                ObjectNode result = event("TOOL_CALL_RESULT")
                        .put("messageId", UUID.randomUUID().toString())
                        .put("toolCallId", telemetry.toolCallId())
                        .put("content", telemetry.payload() == null ? "" : telemetry.payload())
                        .put("role", "tool");
                if (telemetry.resultType() != null && !telemetry.resultType().isBlank()) {
                    result.put("resultType", telemetry.resultType());
                }
                if (telemetry.resultPath() != null && !telemetry.resultPath().isBlank()) {
                    result.put("resultPath", telemetry.resultPath());
                }
                yield result;
            }
        };
        if (telemetry.threadId() != null) {
            ev.put("threadId", telemetry.threadId());
        }
        if (telemetry.runId() != null) {
            ev.put("runId", telemetry.runId());
        }
        emit(telemetry.connectionId(), ev);
    }

    void onResponse(@ObservesAsync AguiResponse response) {
        if (response.type() == AguiResponse.Type.ERROR) {
            history.error(HistoryEvent.Channel.AGUI, response.userId(), response.username(), response.errorCode(),
                    response.message(), response.connectionId(), response.model());
            emit(response.connectionId(), event("RUN_ERROR")
                    .put("threadId", response.threadId())
                    .put("runId", response.runId())
                    .put("code", response.errorCode())
                    .put("message", response.message()));
            return;
        }
        history.response(HistoryEvent.Channel.AGUI, response.userId(), response.username(), response.message(),
                response.connectionId(), response.model());
        String messageId = UUID.randomUUID().toString();
        emit(response.connectionId(),
                event("TEXT_MESSAGE_START")
                        .put("messageId", messageId)
                        .put("role", "assistant"),
                event("TEXT_MESSAGE_CONTENT")
                        .put("messageId", messageId)
                        .put("delta", response.message()),
                event("TEXT_MESSAGE_END").put("messageId", messageId),
                runFinished(response));
    }

    private ObjectNode runFinished(AguiResponse response) {
        ObjectNode finished = event("RUN_FINISHED")
                .put("threadId", response.threadId())
                .put("runId", response.runId());
        ObjectNode metadata = mapper.createObjectNode();
        if (response.model() != null && !response.model().isBlank()) {
            metadata.put("mode", response.model());
        }
        if (response.dispatchWaitMs() != null) {
            metadata.put("dispatchWaitMs", response.dispatchWaitMs());
        }
        if (response.aiMs() != null) {
            metadata.put("aiMs", response.aiMs());
        }
        if (response.dispatchWaitMs() != null && response.aiMs() != null) {
            metadata.put("serverTotalMs", response.dispatchWaitMs() + response.aiMs());
        }
        if (!metadata.isEmpty()) {
            finished.set("metadata", metadata);
        }
        if (hasTokenUsage(response)) {
            ObjectNode usageEntry = mapper.createObjectNode();
            if (response.providerModelId() != null) {
                usageEntry.put("model", response.providerModelId());
            }
            if (response.inputTokens() != null) {
                usageEntry.put("inputTokens", response.inputTokens());
            }
            if (response.outputTokens() != null) {
                usageEntry.put("outputTokens", response.outputTokens());
            }
            if (response.totalTokens() != null) {
                usageEntry.put("totalTokens", response.totalTokens());
            }
            ArrayNode usage = mapper.createArrayNode();
            usage.add(usageEntry);
            finished.set("usage", usage);
        }
        return finished;
    }

    private static boolean hasTokenUsage(AguiResponse response) {
        return response.inputTokens() != null
                || response.outputTokens() != null
                || response.totalTokens() != null;
    }

    public static String memoryId(String userId, String threadId) {
        return userId + "|" + threadId;
    }

    private static String subject(Principal principal) {
        return principal instanceof JsonWebToken token ? token.getSubject() : principal.getName();
    }

    private static String readableName(Principal principal) {
        if (principal instanceof JsonWebToken token) {
            String preferred = token.getClaim("preferred_username");
            if (preferred != null && !preferred.isBlank()) {
                return preferred;
            }
        }
        return principal.getName();
    }

    private String lastUserMessage(JsonNode input) {
        JsonNode messages = input.path("messages");
        for (int i = messages.size() - 1; i >= 0; i--) {
            JsonNode message = messages.get(i);
            if ("user".equals(message.path("role").asText())) {
                String content = message.path("content").asText("");
                return content.isBlank() ? null : content;
            }
        }
        return null;
    }

    private static String replyLanguage(JsonNode input) {
        String language = input.path("forwardedProps").path("language").asText("").trim();
        return "en".equalsIgnoreCase(language) ? "en" : "it";
    }

    private static String modelMode(JsonNode input) {
        String model = input.path("forwardedProps").path("model").asText("").trim();
        return "pro".equalsIgnoreCase(model) ? "pro" : "quick";
    }

    private String projectUuid(JsonNode input) {
        String uuid = input.path("forwardedProps").path("projectUuid").asText("").trim();
        if (uuid.isEmpty()) {
            return null;
        }
        try {
            return projectAccess.findByUuid(uuid)
                    .filter(project -> projectAccess.canAccess(identity, project))
                    .map(p -> p.uuid)
                    .orElse(null);
        } catch (Exception failure) {
            Log.warnf(failure, "Project lookup failed for %s", uuid);
            return null;
        }
    }

    private ObjectNode event(String type) {
        return mapper.createObjectNode()
                .put("type", type)
                .put("timestamp", System.currentTimeMillis());
    }

    private void emitRunError(String connectionId, String message) {
        emit(connectionId, event("RUN_ERROR").put("message", message));
    }

    private void emit(String connectionId, ObjectNode... events) {
        WebSocketConnection connection = connections.get(connectionId);
        if (connection == null || !connection.isOpen()) {
            Log.warnf("No open AG-UI session: %s", connectionId);
            return;
        }
        Uni<Void> stream = Uni.createFrom().voidItem();
        for (ObjectNode event : events) {
            String payload = event.toString();
            stream = stream.chain(() -> connection.sendText(payload));
        }
        stream.subscribe().with(
                ignored -> {
                },
                failure -> Log.errorf(failure, "Failed to deliver AG-UI events to session: %s", connectionId));
    }

    private static String text(JsonNode input, String field, String fallback) {
        String value = input.path(field).asText("");
        return value.isBlank() ? fallback : value;
    }

    private void forget(WebSocketConnection connection) {
        if (connections.remove(connection.id()) == null) {
            return;
        }
        history.logout(HistoryEvent.Channel.AGUI, connection.userData().get(USER_ID),
                connection.userData().get(USERNAME), connection.id());
        Set<String> closed = memories.remove(connection.id());
        if (closed != null) {
            closed.forEach(memoryId -> {
                ChatMemoryRemover.remove(agentService, memoryId);
                ChatMemoryRemover.remove(proAgentService, memoryId);
            });
        }
    }
}
