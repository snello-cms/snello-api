package io.snello.security;

import io.quarkus.logging.Log;
import io.snello.model.pojo.UserEnabledState;
import io.snello.service.AuthService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Answers whether a Keycloak user is still enabled. A JWT carries no such claim, so a user disabled (or deleted)
 * in Keycloak would keep full access until the access token expires; {@link UserEnabledAugmentor} asks this
 * service on every authenticated request.
 * <p>
 * Answers are cached per user id for {@code snello.security.user-enabled-cache-seconds}, i.e. a disabled user is
 * locked out within that time. When Keycloak cannot be reached the check fails <em>open</em> (the API must not go
 * down with the admin endpoint) and that answer is cached only briefly.
 */
@ApplicationScoped
public class UserEnabledService {

    private static final long FAILURE_CACHE_MILLIS = 10_000;

    @Inject
    AuthService authService;

    @ConfigProperty(name = "snello.security.check-user-enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "snello.security.user-enabled-cache-seconds", defaultValue = "60")
    long cacheSeconds;

    @ConfigProperty(name = "snello.keycloak.admin.target-realm")
    String targetRealm;

    private final ConcurrentHashMap<String, UserEnabledState> cache = new ConcurrentHashMap<>();

    /** The check is on, and the token was issued by the realm whose users the admin client can look up. */
    public boolean applies(String issuer) {
        return enabled && issuer != null && issuer.endsWith("/realms/" + targetRealm);
    }

    /** The cached answer, or {@code null} when it is missing or expired (the caller must then {@link #resolve}). */
    public Boolean cached(String userId) {
        UserEnabledState state = cache.get(userId);
        if (state == null || state.expired(System.currentTimeMillis())) {
            return null;
        }
        return state.enabled();
    }

    /** Blocking: asks Keycloak and caches the answer. Must not run on the event loop. */
    public boolean resolve(String userId) {
        long now = System.currentTimeMillis();
        try {
            boolean userEnabled = authService.isUserEnabled(userId);
            cache.put(userId, new UserEnabledState(userEnabled, now + cacheSeconds * 1000));
            Log.infof("user-enabled check userId=%s enabled=%s (cached %ss)", userId, userEnabled, cacheSeconds);
            return userEnabled;
        } catch (RuntimeException e) {
            Log.warnf(e, "user-enabled check userId=%s failed, letting the request through: %s", userId, e.getMessage());
            cache.put(userId, new UserEnabledState(true, now + Math.min(FAILURE_CACHE_MILLIS, cacheSeconds * 1000)));
            return true;
        }
    }
}
