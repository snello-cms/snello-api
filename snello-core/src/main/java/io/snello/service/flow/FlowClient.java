package io.snello.service.flow;

import io.quarkus.logging.Log;
import io.snello.model.pojo.FlowProxyResponse;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * HTTP client to the Quarkus Flow runner ({@code snello.flow.url}, env {@code SNELLO_FLOW_URL}, the same URL the
 * Actions start workflows with) and to flow-bridge ({@code snello.flow.bridge-url}, env
 * {@code SNELLO_FLOW_BRIDGE_URL}: which task each instance is on). Both are internal services: snello forwards to them
 * so the admin never talks to them directly and the Keycloak roles of {@code /api/flow} apply.
 * <p>
 * The base URLs come from configuration only (never from the request), so this is not an open proxy.
 */
@ApplicationScoped
public class FlowClient {

    @ConfigProperty(name = "snello.flow.url")
    Optional<String> runnerUrl;

    @ConfigProperty(name = "snello.flow.bridge-url")
    Optional<String> bridgeUrl;

    @ConfigProperty(name = "snello.flow.timeout-seconds", defaultValue = "10")
    int timeoutSeconds;

    /** flow-bridge BRIDGE_WRITE_TOKEN: workflow deploy (PUT/DELETE /workflows/...). Unset = deploy off. */
    @ConfigProperty(name = "snello.flow.bridge-token")
    Optional<String> bridgeToken;

    /** Upper bound of a deploy: flow-bridge waits up to its DEPLOY_TIMEOUT_SECONDS (20) for the runner to load it. */
    @ConfigProperty(name = "snello.flow.deploy-timeout-seconds", defaultValue = "40")
    int deployTimeoutSeconds;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public FlowProxyResponse runner(String method, String pathAndQuery) {
        return call("runner", runnerUrl, method, pathAndQuery);
    }

    public FlowProxyResponse bridge(String method, String pathAndQuery) {
        return call("flow-bridge", bridgeUrl, method, pathAndQuery);
    }

    /**
     * Workflow deploy on flow-bridge: {@code PUT /workflows/{ns}/{name}/{version}} with the YAML (it waits for the
     * runner to register it) or {@code DELETE} of the same path. 503 if the bridge URL or token is not configured.
     */
    public FlowProxyResponse bridgeWorkflow(String method, String namespace, String name, String version, String yaml) {
        String token = bridgeToken.map(String::trim).filter(s -> !s.isEmpty()).orElse(null);
        if (token == null) {
            Log.warnf("flow-bridge %s workflow %s:%s:%s: no token (SNELLO_FLOW_BRIDGE_TOKEN)", method, namespace, name, version);
            return new FlowProxyResponse(503, "{\"error\":\"workflow deploy is not configured on this snello "
                    + "(SNELLO_FLOW_BRIDGE_TOKEN)\"}", "application/json");
        }
        return call("flow-bridge", bridgeUrl, method, "/workflows/" + namespace + "/" + name + "/" + version,
                yaml, token, deployTimeoutSeconds);
    }

    private FlowProxyResponse call(String target, Optional<String> base, String method, String pathAndQuery) {
        return call(target, base, method, pathAndQuery, null, null, timeoutSeconds);
    }

    private FlowProxyResponse call(String target, Optional<String> base, String method, String pathAndQuery,
                                   String yamlBody, String token, int timeout) {
        String root = base.map(String::trim).filter(s -> !s.isEmpty()).map(s -> s.replaceAll("/+$", "")).orElse(null);
        if (root == null) {
            Log.warnf("flow %s %s %s: not configured", target, method, pathAndQuery);
            return new FlowProxyResponse(503, "{\"error\":\"" + target + " is not configured on this snello ("
                    + ("runner".equals(target) ? "SNELLO_FLOW_URL" : "SNELLO_FLOW_BRIDGE_URL") + ")\"}",
                    "application/json");
        }
        URI uri = URI.create(root + pathAndQuery);
        long start = System.currentTimeMillis();
        Log.infof("flow %s %s %s", target, method, uri);
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(timeout))
                    .header("Accept", "application/json");
            if (token != null) {
                req.header("X-Bridge-Token", token);
            }
            if ("PUT".equals(method)) {
                req.header("Content-Type", "application/yaml")
                        .PUT(HttpRequest.BodyPublishers.ofString(yamlBody == null ? "" : yamlBody, StandardCharsets.UTF_8));
            } else if ("DELETE".equals(method)) {
                req.DELETE();
            } else if ("POST".equals(method)) {
                req.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}"));
            } else {
                req.GET();
            }
            HttpResponse<String> resp = client.send(req.build(), HttpResponse.BodyHandlers.ofString());
            String type = resp.headers().firstValue("Content-Type").orElse("application/json");
            Log.infof("flow %s %s %s -> %d in %d ms, %d chars", target, method, uri, resp.statusCode(),
                    System.currentTimeMillis() - start, resp.body() == null ? 0 : resp.body().length());
            return new FlowProxyResponse(resp.statusCode(), resp.body(), type);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return unreachable(target, uri, e);
        } catch (Exception e) {
            return unreachable(target, uri, e);
        }
    }

    private FlowProxyResponse unreachable(String target, URI uri, Exception e) {
        Log.errorf(e, "flow %s %s unreachable", target, uri);
        // a refused connection has no message (ConnectException: null): name the exception instead
        String raw = e.getMessage() != null ? e.getMessage()
                : e.getCause() != null && e.getCause().getMessage() != null ? e.getCause().getMessage()
                : e.getClass().getSimpleName() + " (connection refused or closed)";
        String msg = raw.replace("\\", "\\\\").replace("\"", "'");
        return new FlowProxyResponse(503, "{\"error\":\"" + target + " unreachable: " + msg + "\"}", "application/json");
    }
}
