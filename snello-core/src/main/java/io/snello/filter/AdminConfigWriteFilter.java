package io.snello.filter;

import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import io.snello.api.service.AbstractServiceRs;
import jakarta.annotation.Priority;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.util.Set;

/**
 * Writes to the configuration resources ({@link AbstractServiceRs} subclasses: metadatas, fielddefinitions,
 * conditions, actions, selectqueries, links, AI projects / skills / tools / cards / service accounts) require
 * Admin or Manager.
 * <p>
 * The HTTP policy lets any authenticated {@code User} POST/PUT/DELETE on {@code /api/*}, which is right for the data
 * API but let a plain user define raw-SQL select queries and conditions (reading every owner's rows) and server-side
 * JavaScript actions. Enforced here, once, so subclasses that override {@code persist}/{@code update}/{@code delete}
 * cannot forget the check.
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class AdminConfigWriteFilter implements ContainerRequestFilter {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Set<String> CONFIG_WRITE_ROLES = Set.of("Admin", "admin", "Manager", "manager");

    /** Off only for auth-less test stacks (OIDC disabled), like the HTTP role policies there. */
    @ConfigProperty(name = "snello.security.config-writes.admin-only", defaultValue = "true")
    boolean adminOnly;

    @Context
    ResourceInfo resourceInfo;

    @Inject
    SecurityIdentity identity;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String method = ctx.getMethod();
        if (!adminOnly || READ_METHODS.contains(method)) {
            return;
        }
        Class<?> resource = resourceInfo == null ? null : resourceInfo.getResourceClass();
        if (resource == null || !AbstractServiceRs.class.isAssignableFrom(resource)) {
            return;
        }
        String path = ctx.getUriInfo().getPath();
        if (identity == null || identity.isAnonymous()) {
            Log.infof("config write %s %s: anonymous -> 401", method, path);
            ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
            return;
        }
        boolean allowed = CONFIG_WRITE_ROLES.stream().anyMatch(identity::hasRole);
        if (!allowed) {
            Log.infof("config write %s %s: user=%s roles=%s is not Admin/Manager -> 403", method, path,
                    identity.getPrincipal().getName(), identity.getRoles());
            ctx.abortWith(Response.status(Response.Status.FORBIDDEN).build());
            return;
        }
        Log.debugf("config write %s %s: user=%s allowed", method, path, identity.getPrincipal().getName());
    }
}
