package io.snello.aio;

import io.quarkus.logging.Log;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Serves rewritten admin SPA config from {@code /assets/config.json} (and a stub
 * {@code /assets/config.local.json}) so Docker-copied snello-admin assets talk same-origin
 * {@code /api} instead of whatever host was baked into the admin image (e.g. kayak.love).
 */
@ApplicationScoped
public class AdminConfigRewrite {

    private static final int ORDER = -2_100;

    @ConfigProperty(name = "snello.admin-ui.path", defaultValue = "/deployments/admin-ui")
    String adminUiPath;

    @ConfigProperty(name = "snello.admin-ui.rewrite-config", defaultValue = "true")
    boolean rewriteConfig;

    @ConfigProperty(name = "snello.admin-ui.keycloak.url")
    Optional<String> keycloakUrl;

    @ConfigProperty(name = "snello.admin-ui.keycloak.realm")
    Optional<String> keycloakRealm;

    @ConfigProperty(name = "snello.admin-ui.keycloak.client-id")
    Optional<String> keycloakClientId;

    void register(@Observes Router router) {
        if (!rewriteConfig) {
            return;
        }
        Path configPath = Path.of(adminUiPath, "assets", "config.json");
        if (!Files.isRegularFile(configPath)) {
            Log.debugf("admin config rewrite skipped: %s missing", configPath);
            return;
        }

        router.get("/assets/config.json").order(ORDER).handler(ctx -> {
            try {
                ctx.response()
                        .putHeader("Content-Type", "application/json; charset=UTF-8")
                        .putHeader("Cache-Control", "no-store")
                        .end(Buffer.buffer(rewrittenConfig(configPath).encodePrettily()));
            } catch (Exception e) {
                Log.error("failed to rewrite admin config.json", e);
                ctx.next();
            }
        });

        // SPA often probes config.local.json first; 404 is noisy — return empty object.
        router.get("/assets/config.local.json").order(ORDER).handler(ctx -> ctx.response()
                .putHeader("Content-Type", "application/json; charset=UTF-8")
                .putHeader("Cache-Control", "no-store")
                .end("{}"));

        Log.info("admin SPA config rewrite enabled (/assets/config.json → same-origin /api)");
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
        keycloakUrl.filter(v -> v != null && !v.isBlank()).ifPresent(v -> kc.put("url", v));
        keycloakRealm.filter(v -> v != null && !v.isBlank()).ifPresent(v -> kc.put("realm", v));
        keycloakClientId.filter(v -> v != null && !v.isBlank()).ifPresent(v -> kc.put("clientId", v));
        cfg.put("keycloakConfig", kc);
        return cfg;
    }

    static String stripOrigin(String url) {
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return url;
        }
        int pathStart = url.indexOf('/', scheme + 3);
        return pathStart < 0 ? "/" : url.substring(pathStart);
    }
}
