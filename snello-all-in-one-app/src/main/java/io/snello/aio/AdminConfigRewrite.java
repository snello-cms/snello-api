package io.snello.aio;

import io.quarkus.logging.Log;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Serves rewritten admin SPA config from {@code /assets/config.json} and
 * {@code /assets/config.local.json} so Docker-copied snello-admin assets talk same-origin
 * {@code /api} instead of whatever host was baked into the admin image (e.g. kayak.love).
 * <p>
 * Both paths return the <em>full</em> rewritten JSON (paths + keycloak). The SPA prefers
 * {@code config.local.json} when it returns JSON 200 — an empty {@code {}} previously caused a
 * blank page; omitting the route caused a noisy 404 then fallback to {@code config.json}.
 * <p>
 * {@code keycloakConfig.url} / {@code realm} come from the API OIDC issuer
 * ({@code quarkus.oidc.auth-server-url}); {@code clientId} from
 * {@code SNELLO_ADMIN_KEYCLOAK_CLIENT_ID}.
 */
@ApplicationScoped
public class AdminConfigRewrite {

    private static final int ORDER = -2_100;

    @ConfigProperty(name = "snello.admin-ui.path", defaultValue = "/deployments/admin-ui")
    String adminUiPath;

    @ConfigProperty(name = "snello.admin-ui.rewrite-config", defaultValue = "true")
    boolean rewriteConfig;

    @ConfigProperty(name = "quarkus.oidc.auth-server-url")
    Optional<String> oidcAuthServerUrl;

    @ConfigProperty(name = "snello.admin-ui.keycloak.client-id", defaultValue = "snello-admin")
    String keycloakClientId;

    void register(@Observes Router router) {
        if (!rewriteConfig) {
            return;
        }
        Path configPath = Path.of(adminUiPath, "assets", "config.json");
        if (!Files.isRegularFile(configPath)) {
            Log.debugf("admin config rewrite skipped: %s missing", configPath);
            return;
        }

        Handler<RoutingContext> serveRewritten = ctx -> {
            try {
                ctx.response()
                        .putHeader("Content-Type", "application/json; charset=UTF-8")
                        .putHeader("Cache-Control", "no-store")
                        .end(Buffer.buffer(rewrittenConfig(configPath).encodePrettily()));
            } catch (Exception e) {
                Log.error("failed to rewrite admin SPA config", e);
                ctx.next();
            }
        };
        // Before StaticHandler (-2000): overrides any baked config.local.json on disk.
        router.get("/assets/config.json").order(ORDER).handler(serveRewritten);
        router.get("/assets/config.local.json").order(ORDER).handler(serveRewritten);

        Log.info("admin SPA config rewrite enabled (/assets/config.json + config.local.json → same-origin /api)");
    }

    JsonObject rewrittenConfig(Path configPath) throws Exception {
        JsonObject cfg = new JsonObject(Files.readString(configPath));
        for (String key : cfg.fieldNames().toArray(String[]::new)) {
            if (!key.endsWith("_path")) {
                continue;
            }
            Object value = cfg.getValue(key);
            if (value instanceof String s && (s.startsWith("http://") || s.startsWith("https://"))) {
                cfg.put(key, stripOrigin(s));
            }
        }
        JsonObject kc = cfg.getJsonObject("keycloakConfig", new JsonObject());
        oidcAuthServerUrl
                .filter(v -> v != null && !v.isBlank())
                .map(AdminConfigRewrite::parseOidcIssuer)
                .ifPresent(issuer -> {
                    kc.put("url", issuer.url());
                    kc.put("realm", issuer.realm());
                });
        if (keycloakClientId != null && !keycloakClientId.isBlank()) {
            kc.put("clientId", keycloakClientId);
        }
        cfg.put("keycloakConfig", kc);
        return cfg;
    }

    /**
     * Parses {@code https://host/realms/myrealm} into Keycloak SPA url + realm.
     * Trailing slash on url matches baked admin config style.
     */
    static OidcIssuer parseOidcIssuer(String authServerUrl) {
        String trimmed = authServerUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        String marker = "/realms/";
        int idx = trimmed.indexOf(marker);
        if (idx < 0) {
            return new OidcIssuer(ensureTrailingSlash(trimmed), "");
        }
        String base = trimmed.substring(0, idx);
        String realm = trimmed.substring(idx + marker.length());
        int slash = realm.indexOf('/');
        if (slash >= 0) {
            realm = realm.substring(0, slash);
        }
        return new OidcIssuer(ensureTrailingSlash(base), realm);
    }

    private static String ensureTrailingSlash(String url) {
        return url.endsWith("/") ? url : url + "/";
    }

    static String stripOrigin(String url) {
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return url;
        }
        int pathStart = url.indexOf('/', scheme + 3);
        return pathStart < 0 ? "/" : url.substring(pathStart);
    }

    record OidcIssuer(String url, String realm) {
    }
}
