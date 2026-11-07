package io.snello.service.ai.pojo;

public record AguiResponse(
        String connectionId,
        String userId,
        String username,
        String threadId,
        String runId,
        Type type,
        String errorCode,
        String message,
        String model,
        String providerModelId,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Long dispatchWaitMs,
        Long aiMs) {

    public enum Type { SUCCESS, ERROR }

    public static AguiResponse success(
            AguiRequest request,
            String message,
            String providerModelId,
            Integer inputTokens,
            Integer outputTokens,
            Integer totalTokens,
            Long dispatchWaitMs,
            Long aiMs) {
        return new AguiResponse(
                request.connectionId(), request.userId(), request.username(),
                request.threadId(), request.runId(), Type.SUCCESS, null, message,
                request.model(), providerModelId, inputTokens, outputTokens, totalTokens,
                dispatchWaitMs, aiMs);
    }

    public static AguiResponse error(
            AguiRequest request, String errorCode, String message, Long dispatchWaitMs, Long aiMs) {
        return new AguiResponse(
                request.connectionId(), request.userId(), request.username(),
                request.threadId(), request.runId(), Type.ERROR, errorCode, message,
                request.model(), null, null, null, null, dispatchWaitMs, aiMs);
    }
}
