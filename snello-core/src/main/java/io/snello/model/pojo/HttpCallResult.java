package io.snello.model.pojo;

import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Result of an HTTP call. Scripts don't see this class directly: the {@code call()} wrapper in ScriptService exposes
 * the same methods, but its {@code result()} is parsed with JS {@code JSON.parse} so scripts get real JS objects.
 * {@link #result()} here is for Java callers.
 * <p>
 * Never throws for non-2xx: check {@link #ok()} / {@link #status()}. Transport failures (timeout, DNS, blocked host)
 * come back as status 0 with {@link #error()} set.
 */
public class HttpCallResult {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final int status;
    private final String body;
    private final String error;

    public HttpCallResult(int status, String body, String error) {
        this.status = status;
        this.body = body;
        this.error = error;
    }

    public int status() {
        return status;
    }

    public boolean ok() {
        return status >= 200 && status < 300;
    }

    public String body() {
        return body;
    }

    public String error() {
        return error;
    }

    /** Body parsed as JSON (Map / List / value) when it is JSON, otherwise the raw string. */
    public Object result() {
        if (body == null || body.isBlank()) {
            return null;
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return MAPPER.readValue(trimmed, Object.class);
            } catch (Exception e) {
                return body;
            }
        }
        return body;
    }

    @Override
    public String toString() {
        return "HttpCallResult{status=" + status + ", error=" + error + ", bodyLength=" + (body == null ? 0 : body.length()) + "}";
    }
}
