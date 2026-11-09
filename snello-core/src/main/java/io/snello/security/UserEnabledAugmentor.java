package io.snello.security;

import io.quarkus.logging.Log;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Rejects (401) a request whose token belongs to a user that is disabled or deleted in Keycloak. Runs for every
 * authenticated request, before any resource, {@code @RolesAllowed} or HTTP policy, so one class covers the data
 * API, the config writes and the row-level security. Anonymous requests and tokens of other realms/tenants pass.
 */
@ApplicationScoped
@Priority(100)
public class UserEnabledAugmentor implements SecurityIdentityAugmentor {

    @Inject
    UserEnabledService userEnabledService;

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (identity == null || identity.isAnonymous() || !(identity.getPrincipal() instanceof JsonWebToken jwt)) {
            return Uni.createFrom().item(identity);
        }
        String userId = jwt.getSubject();
        if (userId == null || !userEnabledService.applies(jwt.getIssuer())) {
            return Uni.createFrom().item(identity);
        }
        Boolean cached = userEnabledService.cached(userId);
        if (cached != null) {
            return Uni.createFrom().item(check(identity, userId, cached));
        }
        // Cache miss: the Keycloak lookup blocks, so leave the event loop.
        return context.runBlocking(() -> check(identity, userId, userEnabledService.resolve(userId)));
    }

    private SecurityIdentity check(SecurityIdentity identity, String userId, boolean userEnabled) {
        if (!userEnabled) {
            Log.warnf("user %s (%s) is disabled or deleted in Keycloak -> 401", identity.getPrincipal().getName(), userId);
            throw new AuthenticationFailedException("user is disabled");
        }
        return identity;
    }
}
