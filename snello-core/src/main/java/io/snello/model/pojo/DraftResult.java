package io.snello.model.pojo;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;
import java.util.Map;

/**
 * Outcome of validating (dry run) or applying an {@link EntityDraft}.
 *
 * @param valid         no errors: the draft can be applied
 * @param errors        blocking problems (nothing is written when present)
 * @param warnings      non-blocking remarks
 * @param metadata      the {@code metadatas} row the draft becomes (derivations included)
 * @param fields        the {@code fielddefinitions} rows (UI derived when not given)
 * @param ddl           CREATE TABLE (+ join tables) that {@code create_table} would execute
 * @param applied       rows were persisted
 * @param table_created the DDL was executed
 */
@RegisterForReflection
public record DraftResult(
        boolean valid,
        List<String> errors,
        List<String> warnings,
        Map<String, Object> metadata,
        List<Map<String, Object>> fields,
        List<String> ddl,
        boolean applied,
        boolean table_created) {

    public DraftResult withApplied(boolean tableCreated) {
        return new DraftResult(valid, errors, warnings, metadata, fields, ddl, true, tableCreated);
    }
}
