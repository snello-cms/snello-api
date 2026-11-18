package io.snello.util;

import io.quarkus.logging.Log;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public class JwtUtils {

    /**
     * The unverified "iss" claim of a bearer token (header value "Bearer xxx.yyy.zzz" or the bare token), used only to
     * pick the OIDC tenant: verification happens afterwards in Quarkus OIDC. Returns "default" when there is no token
     * or it cannot be read, so a malformed token ends as a 401 there instead of a 500 here.
     */
    public static String getIssuer(ObjectMapper objectMapper, String rawToken) {
        if (rawToken == null) {
            return "default";
        }
        String token = rawToken.startsWith("Bearer ") ? rawToken.substring(7).trim() : rawToken.trim();
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            Log.debug("jwt issuer: not a JWT (" + parts.length + " parts)");
            return "default";
        }
        try {
            // JWT segments are base64url without padding: the URL decoder takes them as they are
            byte[] bytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<?, ?> claims = objectMapper.readValue(new String(bytes, StandardCharsets.UTF_8), Map.class);
            Object iss = claims.get("iss");
            return iss instanceof String s ? s : "default";
        } catch (RuntimeException e) {
            Log.info("jwt issuer: unreadable token payload (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
            return "default";
        }
    }
}
