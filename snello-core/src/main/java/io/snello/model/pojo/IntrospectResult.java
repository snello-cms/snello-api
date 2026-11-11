package io.snello.model.pojo;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Outcome of schema introspection (preview or apply).
 *
 * @param valid    no blocking errors (skipped tables are warnings, not errors)
 * @param errors   blocking problems
 * @param warnings global remarks
 * @param entities per-table results (including skipped)
 * @param applied  rows were persisted
 */
@RegisterForReflection
public record IntrospectResult(
        boolean valid,
        List<String> errors,
        List<String> warnings,
        List<IntrospectedEntity> entities,
        boolean applied) {
}
