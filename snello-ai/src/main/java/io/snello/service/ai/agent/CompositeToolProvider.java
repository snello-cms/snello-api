package io.snello.service.ai.agent;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkus.logging.Log;
import io.snello.service.ai.history.HistoryEventPublisher;
import io.snello.service.ai.mcp.McpToolProvider;
import io.snello.service.ai.mcp.McpToolResultShape;
import io.snello.service.ai.pojo.HistoryEvent;
import io.snello.service.ai.skills.SkillToolProvider;
import io.snello.service.ai.tools.DbAiToolsProviderSupplier;
import io.snello.service.ai.wss.ActiveAguiRun;
import io.snello.service.ai.wss.ToolCallTelemetry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class CompositeToolProvider implements ToolProvider {
    public static final int MAX_PAYLOAD_CHARS = 1_200;
    private static final int MAX_HISTORY_ARGS_CHARS = 800;

    @Inject
    SkillToolProvider skills;

    @Inject
    McpToolProvider mcpTools;

    @Inject
    DbAiToolsProviderSupplier dbTools;

    @Inject
    ActiveAguiRun activeRun;

    @Inject
    Event<ToolCallTelemetry> toolTelemetry;

    @Inject
    ToolResultClipper resultClipper;

    @Inject
    HistoryEventPublisher history;

    @Inject
    JsonMapper mapper;

    @Override
    public boolean isDynamic() {
        return true;
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        ToolProviderResult.Builder builder = ToolProviderResult.builder();
        Set<String> names = new LinkedHashSet<>();
        addAll(builder, names, skills.provideTools(request));
        addAll(builder, names, mcpTools.provideTools(request));
        addAll(builder, names, dbTools.get().provideTools(request));
        return builder.build();
    }

    private void addAll(ToolProviderResult.Builder builder, Set<String> names, ToolProviderResult result) {
        if (result == null || result.tools() == null) {
            return;
        }
        for (Map.Entry<ToolSpecification, ToolExecutor> entry : result.tools().entrySet()) {
            String name = entry.getKey().name();
            if (!names.add(name)) {
                Log.warnf("Skipping duplicated tool definition: %s", name);
                continue;
            }
            builder.add(entry.getKey(), telemetered(name, entry.getValue()));
        }
    }

    private ToolExecutor telemetered(String toolName, ToolExecutor delegate) {
        return (execution, memoryId) -> {
            String callId = UUID.randomUUID().toString();
            String args = execution.arguments() == null ? "{}" : execution.arguments();
            emit(ToolCallTelemetry.Phase.START, callId, toolName, null, null, null);
            emit(ToolCallTelemetry.Phase.ARGS, callId, toolName, clipArgs(args), null, null);
            Optional<McpToolResultShape> shape = mcpTools.resultShape(toolName);
            long startedAt = System.currentTimeMillis();
            try {
                String result = delegate.execute(execution, memoryId);
                long durationMs = System.currentTimeMillis() - startedAt;
                emit(ToolCallTelemetry.Phase.END, callId, toolName, null, null, null);
                String resultType = shape.map(McpToolResultShape::resultType).orElse(null);
                String resultPath = shape.map(McpToolResultShape::resultPath).orElse(null);
                emit(ToolCallTelemetry.Phase.RESULT, callId, toolName,
                        clipResult(result == null ? "" : result, resultType, resultPath),
                        resultType, resultPath);
                recordHistoryTool(toolName, callId, args, resultType, true, durationMs, result);
                return result;
            } catch (RuntimeException failure) {
                long durationMs = System.currentTimeMillis() - startedAt;
                emit(ToolCallTelemetry.Phase.END, callId, toolName, null, null, null);
                emit(ToolCallTelemetry.Phase.RESULT, callId, toolName,
                        clipArgs("Tool failed: " + failure.getMessage()), null, null);
                recordHistoryTool(toolName, callId, args, null, false, durationMs, null);
                throw failure;
            }
        };
    }

    private void recordHistoryTool(String toolName, String callId, String args, String resultType, boolean ok,
            long durationMs, String result) {
        if (!activeRun.isBound()) {
            return;
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("tool", toolName);
            body.put("callId", callId);
            body.put("ok", ok);
            body.put("durationMs", durationMs);
            if (activeRun.runId() != null && !activeRun.runId().isBlank()) {
                body.put("runId", activeRun.runId());
            }
            if (resultType != null && !resultType.isBlank()) {
                body.put("resultType", resultType);
            }
            JsonNode argsNode = tryParseJson(args);
            if (argsNode != null) {
                String serialized = mapper.writeValueAsString(argsNode);
                if (serialized.length() <= MAX_HISTORY_ARGS_CHARS) {
                    body.set("args", argsNode);
                } else {
                    body.put("args", clipForHistory(serialized));
                }
            } else if (args != null && !args.isBlank()) {
                body.put("args", clipForHistory(args));
            }
            Integer itemCount = countItems(result);
            if (itemCount != null) {
                body.put("itemCount", itemCount);
            }
            history.tool(
                    HistoryEvent.Channel.AGUI,
                    activeRun.userId(),
                    activeRun.username(),
                    mapper.writeValueAsString(body),
                    activeRun.connectionId(),
                    activeRun.model());
        } catch (Exception failure) {
            Log.debugf(failure, "History tool event skipped for %s", toolName);
        }
    }

    private JsonNode tryParseJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer countItems(String result) {
        if (result == null || result.isBlank()) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(result);
            if (root == null) {
                return null;
            }
            if (root.isArray()) {
                return root.size();
            }
            JsonNode items = root.get("items");
            if (items != null && items.isArray()) {
                return items.size();
            }
            if (root.has("total") && root.get("total").isNumber()) {
                return root.get("total").asInt();
            }
            if (root.has("count") && root.get("count").isNumber()) {
                return root.get("count").asInt();
            }
        } catch (Exception ignored) {
            // not JSON
        }
        return null;
    }

    private static String clipForHistory(String value) {
        if (value.length() <= MAX_HISTORY_ARGS_CHARS) {
            return value;
        }
        return value.substring(0, MAX_HISTORY_ARGS_CHARS) + "…";
    }

    private void emit(ToolCallTelemetry.Phase phase, String callId, String toolName, String payload,
            String resultType, String resultPath) {
        if (!activeRun.isBound()) {
            return;
        }
        toolTelemetry.fire(new ToolCallTelemetry(
                activeRun.connectionId(),
                activeRun.threadId(),
                activeRun.runId(),
                phase,
                callId,
                toolName,
                payload,
                resultType,
                resultPath));
    }

    private String clipArgs(String value) {
        if (value.length() <= MAX_PAYLOAD_CHARS) {
            return value;
        }
        return value.substring(0, MAX_PAYLOAD_CHARS) + "…";
    }

    private String clipResult(String value, String resultType, String resultPath) {
        return resultClipper.clip(value, resultType, resultPath);
    }
}
