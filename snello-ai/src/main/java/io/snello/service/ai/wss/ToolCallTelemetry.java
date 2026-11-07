package io.snello.service.ai.wss;

public record ToolCallTelemetry(
        String connectionId,
        String threadId,
        String runId,
        Phase phase,
        String toolCallId,
        String toolCallName,
        String payload,
        String resultType,
        String resultPath) {

    public enum Phase { START, ARGS, END, RESULT }
}
