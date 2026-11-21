package io.snello.model.pojo;

import io.quarkus.runtime.annotations.RegisterForReflection;

/** One append-only log line for an introspect batch job. */
@RegisterForReflection
public record IntrospectJobLog(
        String uuid,
        String job_uuid,
        String created_at,
        String level,
        String table_name,
        String message) {
}
