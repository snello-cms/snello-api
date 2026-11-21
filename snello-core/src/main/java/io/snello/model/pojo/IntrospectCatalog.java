package io.snello.model.pojo;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Cheap catalog of user tables for the introspect SPA (no column/sample work).
 *
 * @param total               non-reserved physical tables
 * @param available           not yet referenced by metadata
 * @param already_referenced  already owned by metadata
 * @param tables              filtered entries (optional {@code q})
 */
@RegisterForReflection
public record IntrospectCatalog(
        int total,
        int available,
        int already_referenced,
        List<IntrospectTableEntry> tables) {
}
