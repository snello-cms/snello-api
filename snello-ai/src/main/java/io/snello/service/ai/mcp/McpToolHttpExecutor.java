package io.snello.service.ai.mcp;

import io.quarkus.logging.Log;
import io.snello.model.ai.AiMcpTool;
import io.snello.model.ai.AiServiceAccount;
import io.snello.service.ApiService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.snello.management.AppConstants.AI_SERVICE_ACCOUNT;
import static io.snello.management.AppConstants.UUID;

@ApplicationScoped
public class McpToolHttpExecutor {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Inject
    JsonMapper mapper;

    @Inject
    ApiService apiService;

    @Inject
    ServiceAccountTokenService tokens;

    @ConfigProperty(name = "snello.ai.mcp.http.base-url", defaultValue = "http://127.0.0.1:8080")
    String baseUrl;

    public String execute(AiMcpTool tool, String argumentsJson) {
        try {
            JsonNode args = parseArgs(argumentsJson);
            Map<String, String> argNames = stringMap(tool.arg_names_json);
            Map<String, String> fixedQuery = stringMap(tool.fixed_query_json);

            String resolvedUrl = resolveUrl(tool.url);
            resolvedUrl = substitutePath(resolvedUrl, args, argNames);

            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .timeout(Duration.ofSeconds(30));

            String method = tool.http_method == null ? "GET" : tool.http_method.trim().toUpperCase();
            ArgBinding binding = ArgBinding.parse(tool.arg_binding);
            if (binding == ArgBinding.BODY && !"GET".equals(method) && !"HEAD".equals(method)) {
                ObjectNode body = mapper.createObjectNode();
                copyBoundArgs(args, argNames, body);
                request.uri(URI.create(withFixedQuery(resolvedUrl, fixedQuery)))
                        .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json");
            } else {
                Map<String, String> query = new LinkedHashMap<>(fixedQuery);
                copyBoundArgs(args, argNames, query);
                request.uri(URI.create(appendQuery(resolvedUrl, query)))
                        .method(method, HttpRequest.BodyPublishers.noBody())
                        .header("Accept", "application/json");
            }

            applyAuth(request, tool);

            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                Log.warnf("MCP tool %s returned HTTP %s", tool.name, response.statusCode());
                return "The tool '%s' failed with HTTP %s.".formatted(tool.name, response.statusCode());
            }
            return enrich(response.body(), tool.catalog_page_path_template);
        } catch (Exception failure) {
            Log.warnf(failure, "MCP tool %s failed", tool.name);
            return "The tool '%s' could not be executed: %s".formatted(tool.name, failure.getMessage());
        }
    }

    private void applyAuth(HttpRequest.Builder request, AiMcpTool tool) throws Exception {
        if (!tool.authenticated) {
            return;
        }
        if (tool.service_account_uuid == null || tool.service_account_uuid.isBlank()) {
            throw new IllegalStateException("authenticated tool requires a service account");
        }
        Map<String, Object> row = apiService.fetch(null, AI_SERVICE_ACCOUNT, tool.service_account_uuid, UUID);
        if (row == null) {
            throw new IllegalStateException("service account not found: " + tool.service_account_uuid);
        }
        AiServiceAccount account = new AiServiceAccount(row);
        if (!account.enabled) {
            throw new IllegalStateException("service account disabled: " + tool.service_account_uuid);
        }
        request.header("Authorization", "Bearer " + tokens.accessToken(
                account.uuid, account.host, account.realm, account.client_id, account.client_secret));
    }

    private JsonNode parseArgs(String argumentsJson) throws Exception {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return mapper.createObjectNode();
        }
        return mapper.readTree(argumentsJson);
    }

    private String resolveUrl(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return url.startsWith("/") ? base + url : base + "/" + url;
    }

    private String substitutePath(String url, JsonNode args, Map<String, String> argNames) {
        String result = url;
        for (Iterator<String> names = args.propertyNames().iterator(); names.hasNext();) {
            String arg = names.next();
            String value = textValue(args.get(arg));
            if (value == null) {
                continue;
            }
            String httpName = argNames.getOrDefault(arg, arg);
            result = result.replace("{" + httpName + "}", urlEncode(value));
            result = result.replace("{" + arg + "}", urlEncode(value));
        }
        return result;
    }

    private void copyBoundArgs(JsonNode args, Map<String, String> argNames, ObjectNode body) {
        for (Iterator<Map.Entry<String, JsonNode>> fields = args.properties().iterator(); fields.hasNext();) {
            Map.Entry<String, JsonNode> field = fields.next();
            String value = textValue(field.getValue());
            if (value == null) {
                continue;
            }
            body.put(argNames.getOrDefault(field.getKey(), field.getKey()), value);
        }
    }

    private void copyBoundArgs(JsonNode args, Map<String, String> argNames, Map<String, String> query) {
        for (Iterator<Map.Entry<String, JsonNode>> fields = args.properties().iterator(); fields.hasNext();) {
            Map.Entry<String, JsonNode> field = fields.next();
            String value = textValue(field.getValue());
            if (value == null) {
                continue;
            }
            query.put(argNames.getOrDefault(field.getKey(), field.getKey()), value);
        }
    }

    private static String textValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.isValueNode() ? node.asText() : node.toString();
        if (value.isBlank() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }

    private Map<String, String> stringMap(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        Map<String, String> map = new LinkedHashMap<>();
        JsonNode node = mapper.readTree(json);
        node.properties().forEach(entry -> {
            String value = textValue(entry.getValue());
            if (value != null) {
                map.put(entry.getKey(), value);
            }
        });
        return map;
    }

    private String withFixedQuery(String url, Map<String, String> fixedQuery) {
        if (fixedQuery.isEmpty()) {
            return url;
        }
        return appendQuery(url, fixedQuery);
    }

    private String appendQuery(String url, Map<String, String> query) {
        if (query.isEmpty()) {
            return url;
        }
        StringBuilder builder = new StringBuilder(url);
        builder.append(url.contains("?") ? "&" : "?");
        boolean first = true;
        for (Map.Entry<String, String> entry : query.entrySet()) {
            if (!first) {
                builder.append('&');
            }
            first = false;
            builder.append(urlEncode(entry.getKey())).append('=').append(urlEncode(entry.getValue()));
        }
        return builder.toString();
    }

    private String enrich(String body, String catalogPagePathTemplate) throws Exception {
        JsonNode root = mapper.readTree(body);
        String site = trimTrailingSlash(baseUrl);
        if (root.isArray()) {
            for (JsonNode element : root) {
                stamp(element, site, catalogPagePathTemplate);
            }
        } else {
            stamp(root, site, catalogPagePathTemplate);
        }
        return mapper.writeValueAsString(root);
    }

    private void stamp(JsonNode node, String site, String template) {
        if (!(node instanceof ObjectNode object)) {
            return;
        }
        if (template != null && !template.isBlank() && object.hasNonNull("uuid")) {
            String uuid = object.get("uuid").asText();
            object.put("catalogPageUrl", site + template.replace("{uuid}", uuid));
        }
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
