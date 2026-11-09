package io.snello.model.pojo;

/**
 * Cached answer of "is this Keycloak user still enabled?" with its expiry (epoch millis).
 *
 * @param enabled         {@code false} when the user is disabled or deleted
 * @param expiresAtMillis moment after which the answer must be fetched again
 */
public record UserEnabledState(boolean enabled, long expiresAtMillis) {

    public boolean expired(long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }
}
