package io.snello.security;

import io.quarkus.logging.Log;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.annotation.Priority;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps Keycloak {@code resource_access.snello-api.roles}, {@code realm_access.roles} and {@code groups}
 * onto HTTP policy roles {@code Admin|Manager|User}.
 * <p>
 * Nested JWT claims may arrive as {@link Map}, Jakarta JSON, Vert.x JSON, Jackson JsonNode, or only via
 * the raw access-token payload — all are handled so client-credentials tokens work.
 */
@ApplicationScoped
@Priority(1)
public class OidcRolesAugmentor implements SecurityIdentityAugmentor {

    private static final String SNELLO_API_CLIENT = "snello-api";

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (identity == null || identity.isAnonymous()) {
            return Uni.createFrom().item(identity);
        }

        Principal principal = identity.getPrincipal();
        Set<String> derivedRoles = new HashSet<>();

        if (!(principal instanceof JsonWebToken jwt)) {
            Log.warnf("OIDC roles augmentor: principal is not JsonWebToken (%s) existingRoles=%s",
                    principal == null ? "null" : principal.getClass().getName(), identity.getRoles());
            return Uni.createFrom().item(identity);
        }

        addRolesFromResourceAccess(jwt.getClaim("resource_access"), derivedRoles);
        addRolesFromRealmAccess(jwt.getClaim("realm_access"), derivedRoles);
        addRolesFromGroups(jwt.getClaim("groups"), derivedRoles);
        // Fallback: some Quarkus/OIDC stacks expose nested claims only in the raw token
        if (derivedRoles.isEmpty()) {
            Object payload = decodePayload(jwt);
            if (payload != null) {
                addRolesFromResourceAccess(nested(payload, "resource_access"), derivedRoles);
                addRolesFromRealmAccess(nested(payload, "realm_access"), derivedRoles);
                addRolesFromGroups(nested(payload, "groups"), derivedRoles);
            }
        }

        if (derivedRoles.isEmpty()) {
            Object ra = jwt.getClaim("resource_access");
            Log.warnf("OIDC roles augmentor: no Admin/Manager/User derived for principal=%s existingRoles=%s resource_accessType=%s",
                    identity.getPrincipal().getName(), identity.getRoles(),
                    ra == null ? "null" : ra.getClass().getName());
            return Uni.createFrom().item(identity);
        }

        QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(identity);
        for (String role : derivedRoles) {
            builder.addRole(role);
        }
        Log.infof("OIDC roles augmentor: principal=%s +roles=%s (was %s)",
                identity.getPrincipal().getName(), derivedRoles, identity.getRoles());
        return Uni.createFrom().item(builder.build());
    }

    private void addRolesFromResourceAccess(Object resourceAccess, Set<String> outRoles) {
        Object snelloApi = nested(resourceAccess, SNELLO_API_CLIENT);
        if (snelloApi == null) {
            return;
        }
        addRolesFromCollection(nested(snelloApi, "roles"), outRoles);
    }

    private void addRolesFromRealmAccess(Object realmAccess, Set<String> outRoles) {
        addRolesFromCollection(nested(realmAccess, "roles"), outRoles);
    }

    private void addRolesFromGroups(Object groupsClaim, Set<String> outRoles) {
        if (groupsClaim == null) {
            return;
        }
        for (String rawGroup : asStringList(groupsClaim)) {
            String group = normalizeGroup(rawGroup);
            if (!group.isBlank()) {
                mapKnownRole(group, outRoles);
            }
        }
    }

    private void addRolesFromCollection(Object rolesObj, Set<String> outRoles) {
        if (rolesObj == null) {
            return;
        }
        for (String role : asStringList(rolesObj)) {
            if (role == null || role.isBlank()) {
                continue;
            }
            outRoles.add(role);
            mapKnownRole(role, outRoles);
        }
    }

    private void mapKnownRole(String role, Set<String> outRoles) {
        if (role.equalsIgnoreCase("admin")) {
            addAdminRoleVariants(outRoles);
        } else if (role.equalsIgnoreCase("manager")) {
            addManagerRoleVariants(outRoles);
        } else if (role.equalsIgnoreCase("user")) {
            addUserRoleVariants(outRoles);
        }
    }

    private static Object decodePayload(JsonWebToken jwt) {
        try {
            String raw = jwt.getRawToken();
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String[] parts = raw.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] decoded = Base64.getUrlDecoder().decode(pad(parts[1]));
            String json = new String(decoded, StandardCharsets.UTF_8);
            try (var reader = jakarta.json.Json.createReader(new java.io.StringReader(json))) {
                return reader.readObject();
            }
        } catch (Exception e) {
            Log.debugf(e, "OIDC roles augmentor: raw token payload parse failed");
            return null;
        }
    }

    private static String pad(String b64) {
        int mod = b64.length() % 4;
        if (mod == 0) {
            return b64;
        }
        return b64 + "====".substring(mod);
    }

    private static Object nested(Object parent, String key) {
        if (parent == null || key == null) {
            return null;
        }
        if (parent instanceof Map<?, ?> map) {
            return map.get(key);
        }
        if (parent instanceof JsonObject json) {
            return json.containsKey(key) ? toPlain(json.get(key)) : null;
        }
        // Jackson 2/3 JsonNode: path(key) / get(key)
        try {
            var get = parent.getClass().getMethod("get", String.class);
            Object node = get.invoke(parent, key);
            if (node == null) {
                return null;
            }
            // JsonNode.isMissingNode / isNull
            try {
                var isMissing = parent.getClass().getMethod("isMissingNode");
                // method is on the node
                if (Boolean.TRUE.equals(node.getClass().getMethod("isMissingNode").invoke(node))) {
                    return null;
                }
                if (Boolean.TRUE.equals(node.getClass().getMethod("isNull").invoke(node))) {
                    return null;
                }
            } catch (ReflectiveOperationException ignored) {
                // ignore
            }
            return node;
        } catch (ReflectiveOperationException ignored) {
            // fall through
        }
        // Vert.x JsonObject
        try {
            return parent.getClass().getMethod("getValue", String.class).invoke(parent, key);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object toPlain(JsonValue value) {
        if (value == null || value.getValueType() == JsonValue.ValueType.NULL) {
            return null;
        }
        return switch (value.getValueType()) {
            case STRING -> ((JsonString) value).getString();
            case OBJECT -> (JsonObject) value;
            case ARRAY -> (JsonArray) value;
            case TRUE -> Boolean.TRUE;
            case FALSE -> Boolean.FALSE;
            case NUMBER -> value;
            default -> value;
        };
    }

    private static List<String> asStringList(Object rolesObj) {
        if (rolesObj instanceof Collection<?> roles) {
            return roles.stream().map(OidcRolesAugmentor::stringify).filter(s -> s != null && !s.isBlank()).toList();
        }
        if (rolesObj instanceof JsonArray array) {
            return array.stream().map(OidcRolesAugmentor::stringify).filter(s -> s != null && !s.isBlank()).toList();
        }
        // Jackson ArrayNode / Vert.x JsonArray: iterable or size()/getString
        if (rolesObj instanceof Iterable<?> it && !(rolesObj instanceof Map)) {
            List<String> out = new ArrayList<>();
            for (Object o : it) {
                String s = stringify(o);
                if (s != null && !s.isBlank()) {
                    out.add(s);
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        try {
            var size = (int) rolesObj.getClass().getMethod("size").invoke(rolesObj);
            List<String> out = new ArrayList<>(size);
            // ArrayNode.get(i) or JsonArray already handled; try get(int) then asText/getString
            var get = rolesObj.getClass().getMethod("get", int.class);
            for (int i = 0; i < size; i++) {
                Object el = get.invoke(rolesObj, i);
                String s = stringify(el);
                if (s != null && !s.isBlank()) {
                    out.add(s);
                }
            }
            return out;
        } catch (ReflectiveOperationException ignored) {
            String single = stringify(rolesObj);
            return single == null || single.isBlank() ? List.of() : List.of(single);
        }
    }

    private static String stringify(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof JsonString js) {
            return js.getString();
        }
        if (value instanceof JsonValue jv && jv.getValueType() == JsonValue.ValueType.STRING) {
            return ((JsonString) jv).getString();
        }
        // Jackson TextNode.asText()
        try {
            return (String) value.getClass().getMethod("asText").invoke(value);
        } catch (ReflectiveOperationException ignored) {
            // fall through
        }
        try {
            return (String) value.getClass().getMethod("getString").invoke(value);
        } catch (ReflectiveOperationException ignored) {
            return String.valueOf(value);
        }
    }

    private String normalizeGroup(String rawGroup) {
        String normalized = rawGroup.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private void addAdminRoleVariants(Set<String> roles) {
        roles.add("Admin");
        roles.add("admin");
    }

    private void addManagerRoleVariants(Set<String> roles) {
        roles.add("Manager");
        roles.add("manager");
    }

    private void addUserRoleVariants(Set<String> roles) {
        roles.add("User");
        roles.add("user");
    }
}
