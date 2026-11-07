package io.snello.service.ai.skills;

import io.quarkus.security.identity.SecurityIdentity;
import io.snello.model.ai.AiProject;
import io.snello.service.ApiService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedHashMap;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.snello.management.AppConstants.AI_PROJECT;
import static io.snello.management.AppConstants.UUID;

@ApplicationScoped
public class ProjectAccess {

    @Inject
    ApiService apiService;

    public Set<String> tokenClaims(SecurityIdentity identity) {
        Set<String> claims = new LinkedHashSet<>();
        if (identity == null || identity.isAnonymous()) {
            return claims;
        }
        claims.addAll(identity.getRoles());
        Object principal = identity.getPrincipal();
        if (principal instanceof JsonWebToken jwt) {
            addGroups(claims, jwt.getClaim("groups"));
        }
        return claims;
    }

    public List<AiProject> mine(SecurityIdentity identity) throws Exception {
        List<AiProject> accessible = new ArrayList<>();
        Set<String> claims = tokenClaims(identity);
        MultivaluedHashMap<String, String> params = new MultivaluedHashMap<>();
        List<Map<String, Object>> rows = apiService.list(AI_PROJECT, params, "name asc", 500, 0);
        if (rows == null) {
            return accessible;
        }
        for (Map<String, Object> row : rows) {
            AiProject project = new AiProject(row);
            if (project.isAccessibleTo(claims)) {
                accessible.add(project);
            }
        }
        return accessible;
    }

    public boolean canAccess(SecurityIdentity identity, AiProject project) {
        return project != null && project.isAccessibleTo(tokenClaims(identity));
    }

    public boolean canAccessUuid(SecurityIdentity identity, String projectUuid) throws Exception {
        if (projectUuid == null || projectUuid.isBlank()) {
            return false;
        }
        Map<String, Object> row = apiService.fetch(null, AI_PROJECT, projectUuid, UUID);
        if (row == null) {
            return false;
        }
        return canAccess(identity, new AiProject(row));
    }

    public Optional<AiProject> findByUuid(String projectUuid) throws Exception {
        if (projectUuid == null || projectUuid.isBlank()) {
            return Optional.empty();
        }
        Map<String, Object> row = apiService.fetch(null, AI_PROJECT, projectUuid, UUID);
        return row == null ? Optional.empty() : Optional.of(new AiProject(row));
    }

    @SuppressWarnings("unchecked")
    private static void addGroups(Set<String> claims, Object groupsClaim) {
        if (groupsClaim == null) {
            return;
        }
        if (groupsClaim instanceof Collection<?> collection) {
            for (Object entry : collection) {
                if (entry != null) {
                    String value = entry.toString().trim();
                    if (!value.isEmpty()) {
                        claims.add(value);
                        if (value.startsWith("/") && value.length() > 1) {
                            claims.add(value.substring(1));
                        }
                    }
                }
            }
            return;
        }
        String raw = groupsClaim.toString().trim();
        if (!raw.isEmpty()) {
            claims.add(raw);
        }
    }
}
