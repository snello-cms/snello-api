package io.snello.model.pojo;

import io.quarkus.runtime.annotations.RegisterForReflection;

/** Start an auto-apply introspect batch over all remaining user tables. */
@RegisterForReflection
public record IntrospectBatchRequest(Integer sample_rows, String metadata_group) {

    public int sampleRowsOrDefault() {
        int n = sample_rows == null ? 5 : sample_rows;
        if (n < 0) {
            return 0;
        }
        return Math.min(n, 200);
    }
}
