package io.snello.service.http;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.quarkus.logging.Log;
import io.snello.model.pojo.HttpCallResult;
import io.snello.service.ScriptValues;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Generic REST client for scripts (actions and the scheduler), bound as {@code http} and wrapped by the global
 * {@code call(method, url, body)} function:
 * <pre>
 * call(GET, "https://url.to/call").result();
 * call(POST, "https://url.to/call", {a: 1}).result();
 * </pre>
 * Body: a String is sent as is, anything else (Map / script object) as JSON. For JS arrays pass
 * {@code JSON.stringify(arr)}. Headers: {@code call(POST, url, body, {"Authorization": "Bearer x"})}.
 * <p>
 * Safety: loopback / private / link-local targets (incl. the cloud metadata address) are refused unless
 * {@code snello.http.allow-private=true}; if {@code snello.http.allowed-hosts} is set only those hosts are reachable.
 */
@ApplicationScoped
public class HttpCallService {

    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD");
    private static final int MAX_LOG_BODY = 500;

    @ConfigProperty(name = "snello.http.allow-private", defaultValue = "false")
    boolean allowPrivate;

    @ConfigProperty(name = "snello.http.allowed-hosts")
    Optional<List<String>> allowedHosts;

    @ConfigProperty(name = "snello.http.timeout-seconds", defaultValue = "15")
    int timeoutSeconds;

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER) // a redirect could point past the host check
            .build();

    public HttpCallResult call(String method, String url) {
        return call(method, url, null, null);
    }

    public HttpCallResult call(String method, String url, Object body) {
        return call(method, url, body, null);
    }

    public HttpCallResult call(String method, String url, Object body, Map<String, Object> headers) {
        long start = System.currentTimeMillis();
        String logUrl = redact(url);
        Log.infof("HTTP call %s %s bodyPresent=%s headers=%s", method, logUrl, body != null,
                headers == null ? List.of() : headers.keySet());
        try {
            String m = method == null ? "" : method.trim().toUpperCase();
            if (!METHODS.contains(m)) {
                return fail("unsupported method '" + method + "'");
            }
            URI uri = URI.create(url);
            String guard = checkTarget(uri);
            if (guard != null) {
                return fail(guard);
            }
            HttpRequest.Builder rb = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(timeoutSeconds));
            String payload = serialize(body);
            if (payload != null && !payload.isEmpty() && !m.equals("GET") && !m.equals("HEAD")) {
                rb.header("Content-Type", "application/json");
            }
            if (headers != null) {
                headers.forEach((k, v) -> rb.setHeader(k, String.valueOf(v)));
            }
            rb.method(m, payload == null || m.equals("GET") || m.equals("HEAD")
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(payload));
            HttpResponse<String> resp = client.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            String rbody = resp.body();
            Log.infof("HTTP call %s %s -> %d in %d ms, bodyLength=%d", m, logUrl, resp.statusCode(),
                    System.currentTimeMillis() - start, rbody == null ? 0 : rbody.length());
            // response bodies can carry personal data or tokens: only at DEBUG
            Log.debugf("HTTP call %s %s response body=%s", m, logUrl, abbreviate(rbody));
            return new HttpCallResult(resp.statusCode(), rbody, null);
        } catch (Exception e) {
            Log.errorf(e, "HTTP call %s %s failed after %d ms", method, logUrl, System.currentTimeMillis() - start);
            return fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private HttpCallResult fail(String message) {
        Log.warnf("HTTP call refused/failed: %s", message);
        return new HttpCallResult(0, null, message);
    }

    private String serialize(Object body) throws Exception {
        if (body == null) {
            return null;
        }
        if (body instanceof CharSequence s) {
            return s.toString();
        }
        return mapper.writeValueAsString(ScriptValues.toJava(body));
    }

    /** @return null when the target is allowed, otherwise the reason it is refused */
    String checkTarget(URI uri) throws Exception {
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return "only http/https urls are allowed, got '" + scheme + "'";
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return "url has no host: " + uri;
        }
        if (allowedHosts.isPresent() && !allowedHosts.get().isEmpty()
                && allowedHosts.get().stream().noneMatch(h -> h.equalsIgnoreCase(host))) {
            return "host '" + host + "' is not in snello.http.allowed-hosts";
        }
        if (!allowPrivate) {
            for (InetAddress a : InetAddress.getAllByName(host)) {
                if (a.isLoopbackAddress() || a.isAnyLocalAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress()
                        || a.isMulticastAddress()) {
                    return "host '" + host + "' resolves to a private address (" + a.getHostAddress()
                            + "); set snello.http.allow-private=true to allow";
                }
            }
        }
        return null;
    }

    /** Query string and user-info dropped: API keys often travel as {@code ?key=...} or {@code user:pass@host}. */
    static String redact(String url) {
        if (url == null) {
            return null;
        }
        try {
            URI u = URI.create(url);
            String port = u.getPort() == -1 ? "" : ":" + u.getPort();
            String path = u.getRawPath() == null ? "" : u.getRawPath();
            return u.getScheme() + "://" + u.getHost() + port + path + (u.getRawQuery() == null ? "" : "?***");
        } catch (Exception e) {
            int q = url.indexOf('?');
            return q < 0 ? url : url.substring(0, q) + "?***";
        }
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= MAX_LOG_BODY ? s : s.substring(0, MAX_LOG_BODY) + "...(" + s.length() + " chars)";
    }
}
