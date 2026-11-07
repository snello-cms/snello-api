package io.snello.service.ai.mcp;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ServiceAccountTokenService {
    private static final Duration SKEW = Duration.ofSeconds(30);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

    @Inject
    JsonMapper mapper;

    public String accessToken(String accountUuid, String host, String realm, String clientId, String clientSecret) {
        if (accountUuid == null || accountUuid.isBlank()) {
            throw new IllegalArgumentException("service account uuid is required");
        }
        CachedToken cached = cache.get(accountUuid);
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plus(SKEW))) {
            return cached.token();
        }
        CachedToken fresh = fetch(host, realm, clientId, clientSecret);
        cache.put(accountUuid, fresh);
        return fresh.token();
    }

    public void invalidate(String accountUuid) {
        if (accountUuid != null) {
            cache.remove(accountUuid);
        }
    }

    private CachedToken fetch(String host, String realm, String clientId, String clientSecret) {
        try {
            String tokenUrl = host.replaceAll("/+$", "")
                    + "/realms/"
                    + realm
                    + "/protocol/openid-connect/token";
            String body = "grant_type=client_credentials"
                    + "&client_id=" + urlEncode(clientId)
                    + "&client_secret=" + urlEncode(clientSecret);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                Log.warnf("Token request for %s failed with HTTP %s", clientId, response.statusCode());
                throw new IllegalStateException("service account token HTTP " + response.statusCode());
            }
            JsonNode json = mapper.readTree(response.body());
            String token = json.path("access_token").asText(null);
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("token response missing access_token");
            }
            long expiresIn = json.path("expires_in").asLong(300);
            return new CachedToken(token, Instant.now().plusSeconds(Math.max(60, expiresIn)));
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            Log.warnf(failure, "Token request for %s failed", clientId);
            throw new IllegalStateException("service account token request failed: " + failure.getMessage(), failure);
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    record CachedToken(String token, Instant expiresAt) {
    }
}
