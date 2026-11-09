package io.snello.model.pojo;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Row-level security settings of a readable resource: a metadata entity or a named SelectQuery.
 *
 * @param apiProtected  anonymous callers are rejected (401) and non-admin callers are row-filtered
 * @param usernameField column holding the owner username; bound to the JWT username for non-admin callers
 */
@RegisterForReflection
public record RowProtection(boolean apiProtected, String usernameField) {

    /** Row filtering is active only when the resource is protected and names its owner column. */
    public boolean rowLevelSecured() {
        return apiProtected && usernameField != null && !usernameField.isBlank();
    }
}
