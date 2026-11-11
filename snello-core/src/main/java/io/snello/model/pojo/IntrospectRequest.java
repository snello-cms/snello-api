package io.snello.model.pojo;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Request body for {@code /api/introspect/preview} and {@code /api/introspect/apply}.
 * Tables already referenced by current metadata ({@code table_name} or {@code alias_table}) are always skipped.
 *
 * @param tables         optional allow-list (lowercase names); empty/null = every non-system user table not yet in metadata
 * @param sample_rows    rows to sample per table for HTML / long-string heuristics (default 30, max 200)
 * @param metadata_group optional {@code metadatas.metadata_group}
 */
@RegisterForReflection
public record IntrospectRequest(
        List<String> tables,
        Integer sample_rows,
        String metadata_group) {

    public int sampleRowsOrDefault() {
        int n = sample_rows == null ? 30 : sample_rows;
        if (n < 0) {
            return 0;
        }
        return Math.min(n, 200);
    }
}
