package io.snello.model.pojo;

/**
 * Answer of the Quarkus Flow runner or flow-bridge, passed through by {@code /api/flow/*} as it came (status, JSON
 * body, content type). {@code status = 503} with a JSON error body when the service is not configured or unreachable.
 */
public record FlowProxyResponse(int status, String body, String contentType) {
}
