package io.snello.model.pojo;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Imported (outbound) foreign key: {@code fkColumn} on this table references {@code pkTable.pkColumn}.
 */
@RegisterForReflection
public record DbForeignKey(
        String fkColumn,
        String pkTable,
        String pkColumn) {
}
