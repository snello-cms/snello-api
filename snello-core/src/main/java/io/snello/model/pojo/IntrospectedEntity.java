package io.snello.model.pojo;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;
import java.util.Map;

/**
 * One table turned into a metadata + fielddefinitions preview (or applied rows).
 *
 * @param table_name physical / logical table name
 * @param skipped    true when the table was not turned into metadata (bridge, reserved, already exists, …)
 * @param skip_reason human reason when {@code skipped}
 * @param metadata   flat {@code metadatas} row (null when skipped)
 * @param fields     flat {@code fielddefinitions} rows
 * @param warnings   non-blocking remarks for this table
 */
@RegisterForReflection
public record IntrospectedEntity(
        String table_name,
        boolean skipped,
        String skip_reason,
        Map<String, Object> metadata,
        List<Map<String, Object>> fields,
        List<String> warnings) {

    public static IntrospectedEntity skipped(String table, String reason) {
        return new IntrospectedEntity(table, true, reason, null, List.of(), List.of());
    }

    public static IntrospectedEntity skipped(String table, String reason, List<String> warnings) {
        return new IntrospectedEntity(table, true, reason, null, List.of(),
                warnings == null ? List.of() : warnings);
    }
}
