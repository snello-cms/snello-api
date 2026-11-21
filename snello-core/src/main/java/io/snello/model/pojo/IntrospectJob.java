package io.snello.model.pojo;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Persisted introspect batch job status.
 */
@RegisterForReflection
public record IntrospectJob(
        String uuid,
        String status,
        String created_at,
        String started_at,
        String finished_at,
        String created_by,
        String metadata_group,
        int sample_rows,
        int total_tables,
        int done_tables,
        int failed_tables,
        String current_table,
        String error_message) {
}
