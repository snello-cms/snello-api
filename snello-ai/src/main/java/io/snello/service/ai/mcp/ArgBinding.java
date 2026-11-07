package io.snello.service.ai.mcp;

public enum ArgBinding {
    QUERY,
    BODY;

    public static ArgBinding parse(String value) {
        if (value == null || value.isBlank()) {
            return QUERY;
        }
        return "BODY".equalsIgnoreCase(value.trim()) ? BODY : QUERY;
    }
}
