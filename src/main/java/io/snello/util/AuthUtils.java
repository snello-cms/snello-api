package io.snello.util;

import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class AuthUtils {

    private AuthUtils() {
    }

    public static Map<String, Object> userToMap(UserRepresentation user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("email", user.getEmail());
        map.put("name", user.getFirstName());
        map.put("surname", user.getLastName());
        map.put("enabled", user.isEnabled());
        map.put("emailVerified", user.isEmailVerified());
        return map;
    }

    public static Map<String, Object> groupToMap(GroupRepresentation group) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", group.getId());
        map.put("name", group.getName());
        map.put("path", group.getPath());
        map.put("subGroupCount", group.getSubGroupCount());
        return map;
    }

    public static String safeEntity(jakarta.ws.rs.core.Response response) {
        try {
            return Objects.toString(response.readEntity(String.class), "");
        } catch (Exception ignored) {
            return "";
        }
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
