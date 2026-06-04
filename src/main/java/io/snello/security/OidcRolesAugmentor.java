package io.snello.security;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.security.Principal;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class OidcRolesAugmentor implements SecurityIdentityAugmentor {

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
        addRolesFromSnelloApiResourceAccess(jwt, derivedRoles);
        addRolesFromGroups(jwt, derivedRoles);

        if (derivedRoles.isEmpty()) {
            return Uni.createFrom().item(identity);
        }

        QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(identity);
        for (String role : derivedRoles) {
            builder.addRole(role);
        }
        return Uni.createFrom().item(builder.build());
    }

    private void addRolesFromSnelloApiResourceAccess(JsonWebToken jwt, Set<String> outRoles) {
        Object resourceAccessClaim = jwt.getClaim("resource_access");
        if (!(resourceAccessClaim instanceof Map<?, ?> resourceAccessMap)) {
            return;
        }

        Object snelloApi = resourceAccessMap.get("snello-api");
        if (!(snelloApi instanceof Map<?, ?> snelloApiMap)) {
            return;
        }

        Object roles = snelloApiMap.get("roles");
        addRolesFromCollection(roles, outRoles);
    }

    private void addRolesFromGroups(JsonWebToken jwt, Set<String> outRoles) {
        Object groupsClaim = jwt.getClaim("groups");
        if (!(groupsClaim instanceof Collection<?> groups)) {
            return;
        }

        for (Object groupObj : groups) {
            if (!(groupObj instanceof String rawGroup)) {
                continue;
            }

            String group = normalizeGroup(rawGroup);
            if (group.isBlank()) {
                continue;
            }

            if (group.equalsIgnoreCase("admin")) {
                addAdminRoleVariants(outRoles);
            } else if (group.equalsIgnoreCase("manager")) {
                addManagerRoleVariants(outRoles);
            } else if (group.equalsIgnoreCase("user")) {
                addUserRoleVariants(outRoles);
            }
        }
    }

    private void addRolesFromCollection(Object rolesObj, Set<String> outRoles) {
        if (!(rolesObj instanceof Collection<?> roles)) {
            return;
        }

        for (Object roleObj : roles) {
            if (!(roleObj instanceof String role) || role.isBlank()) {
                continue;
            }

            outRoles.add(role);
            if (role.equalsIgnoreCase("admin")) {
                addAdminRoleVariants(outRoles);
            } else if (role.equalsIgnoreCase("manager")) {
                addManagerRoleVariants(outRoles);
            } else if (role.equalsIgnoreCase("user")) {
                addUserRoleVariants(outRoles);
            }
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
