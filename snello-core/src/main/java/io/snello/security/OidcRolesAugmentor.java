package io.snello.security;

import io.quarkus.logging.Log;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.security.Principal;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ensures Keycloak client roles ({@code resource_access.snello-api.roles}), realm roles and
 * {@code groups} map onto HTTP policy roles {@code Admin|Manager|User}.
 * <p>
 * Quarkus may expose nested JWT claims as {@link Map}, Jakarta {@link JsonObject} or Vert.x JsonObject
 * depending on version — all are handled so client-credentials tokens with client role Admin work.
 */
@ApplicationScoped
public class OidcRolesAugmentor implements SecurityIdentityAugmentor {

    private static final String SNELLO_API_CLIENT = "snello-api";

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (identity == null || identity.isAnonymous()) {
            return Uni.createFrom().item(identity);
        }

        Principal principal = identity.getPrincipal();
        if (!(principal instanceof JsonWebToken jwt)) {
            return Uni.createFrom().item(identity);
        }

        Set<String> derivedRoles = new HashSet<>();
        addRolesFromResourceAccess(jwt, derivedRoles);
        addRolesFromRealmAccess(jwt, derivedRoles);
        addRolesFromGroups(jwt, derivedRoles);

        if (derivedRoles.isEmpty()) {
            return Uni.createFrom().item(identity);
        }

        QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(identity);
        for (String role : derivedRoles) {
            builder.addRole(role);
        }
        if (Log.isDebugEnabled()) {
            Log.debugf("OIDC roles augmented for %s: %s (was %s)",
                    identity.getPrincipal().getName(), derivedRoles, identity.getRoles());
        }
        return Uni.createFrom().item(builder.build());
    }

    private void addRolesFromResourceAccess(JsonWebToken jwt, Set<String> outRoles) {
        Object snelloApi = nested(jwt.getClaim("resource_access"), SNELLO_API_CLIENT);
        if (snelloApi == null) {
            return;
        }
        addRolesFromCollection(nested(snelloApi, "roles"), outRoles);
    }

    private void addRolesFromRealmAccess(JsonWebToken jwt, Set<String> outRoles) {
        addRolesFromCollection(nested(jwt.getClaim("realm_access"), "roles"), outRoles);
    }

    private void addRolesFromGroups(JsonWebToken jwt, Set<String> outRoles) {
        Object groupsClaim = jwt.getClaim("groups");
        if (groupsClaim == null) {
            return;
        }
        for (String rawGroup : asStringList(groupsClaim)) {
            String group = normalizeGroup(rawGroup);
            if (group.isBlank()) {
                continue;
            }
            mapKnownRole(group, outRoles);
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

    /** Resolve one nested key from Map / Jakarta JsonObject / Vert.x JsonObject. */
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
        // Vert.x JsonObject (avoid hard dependency): getJsonObject / getValue via reflection-free duck typing
        try {
            var getValue = parent.getClass().getMethod("getValue", String.class);
            return getValue.invoke(parent, key);
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
        // Vert.x JsonArray
        try {
            var size = (int) rolesObj.getClass().getMethod("size").invoke(rolesObj);
            var getString = rolesObj.getClass().getMethod("getString", int.class);
            java.util.ArrayList<String> out = new java.util.ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                String s = (String) getString.invoke(rolesObj, i);
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
        return String.valueOf(value);
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
