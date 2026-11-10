package io.snello.aio;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AdminConfigRewriteTest {

    @Test
    void stripOriginKeepsPath() {
        assertEquals("/api", AdminConfigRewrite.stripOrigin("https://kayak.love/api"));
        assertEquals("/api/metadatas", AdminConfigRewrite.stripOrigin("https://kayak.love/api/metadatas"));
        assertEquals("/", AdminConfigRewrite.stripOrigin("https://accounts.kayak.love/"));
        assertEquals("/api", AdminConfigRewrite.stripOrigin("/api"));
    }

    @Test
    void parseOidcIssuerSplitsUrlAndRealm() {
        AdminConfigRewrite.OidcIssuer issuer =
                AdminConfigRewrite.parseOidcIssuer("https://accounts.example/realms/snello");
        assertEquals("https://accounts.example/", issuer.url());
        assertEquals("snello", issuer.realm());
    }

    @Test
    void parseOidcIssuerHandlesTrailingSlashAndPathSuffix() {
        AdminConfigRewrite.OidcIssuer issuer =
                AdminConfigRewrite.parseOidcIssuer("https://accounts.kayak.love/realms/snello/");
        assertEquals("https://accounts.kayak.love/", issuer.url());
        assertEquals("snello", issuer.realm());
    }

    @Test
    void rewrittenConfigStripsPathsAndSetsKeycloakFromOidc(@TempDir Path dir) throws Exception {
        Path config = dir.resolve("config.json");
        Files.writeString(config, """
                {
                  "actions_api_path": "https://kayak.love/api/actions",
                  "api_service_path": "https://kayak.love/api",
                  "keycloakConfig": {
                    "clientId": "baked-client",
                    "realm": "baked",
                    "url": "https://accounts.kayak.love/"
                  },
                  "scope": "openid"
                }
                """);

        AdminConfigRewrite rewrite = new AdminConfigRewrite();
        rewrite.oidcAuthServerUrl = Optional.of("https://accounts.example/realms/snello");
        rewrite.keycloakClientId = "snello-admin";

        JsonObject cfg = rewrite.rewrittenConfig(config);

        assertEquals("/api/actions", cfg.getString("actions_api_path"));
        assertEquals("/api", cfg.getString("api_service_path"));
        assertEquals("openid", cfg.getString("scope"));
        JsonObject kc = cfg.getJsonObject("keycloakConfig");
        assertEquals("https://accounts.example/", kc.getString("url"));
        assertEquals("snello", kc.getString("realm"));
        assertEquals("snello-admin", kc.getString("clientId"));
        assertFalse(cfg.containsKey("keycloakConfig.url"));
    }
}
