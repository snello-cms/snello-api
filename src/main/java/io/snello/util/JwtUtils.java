package io.snello.util;

import io.quarkus.logging.Log;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class JwtUtils {
    public static String getIssuer(ObjectMapper objectMapper, String rawToken) {
        if (rawToken == null || rawToken.split("\\.").length < 2) {
            return "default";
        }
        String[] tokens = rawToken.split("\\.");
        if (tokens.length < 2) {
            return "default";
        }
        String token = tokens[1];
        String data = token.replace("-", "+").replace("_", "/");

        if (data.length() % 4 == 2) {
            data += "==";
        }
        if (data.length() % 4 == 3) {
            data += "=";
        }

        byte[] bytes = Base64.getUrlDecoder().decode(data);
        String decodedString = new String(bytes, StandardCharsets.UTF_8);

        try {
            Map<String, Object> result = objectMapper.readValue(decodedString, HashMap.class);
            return (String) result.getOrDefault("iss", "default");
        } catch (JacksonException e) {
            Log.error(e.getMessage(), e);
        }
        return "default";
    }
}
