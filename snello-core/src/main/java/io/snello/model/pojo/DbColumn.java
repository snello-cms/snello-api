package io.snello.model.pojo;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * One physical column from {@link java.sql.DatabaseMetaData#getColumns}.
 */
@RegisterForReflection
public record DbColumn(
        String name,
        String typeName,
        int size,
        int decimalDigits,
        boolean nullable,
        boolean autoIncrement,
        String columnDef) {

    /** SQL type string suitable for {@code fielddefinitions.sql_type} (e.g. {@code varchar(80)}). */
    public String sqlType() {
        String t = typeName == null ? "varchar" : typeName.trim().toLowerCase();
        if (t.contains("char") || t.equals("varchar") || t.equals("character varying")) {
            return size > 0 ? "varchar(" + size + ")" : "varchar";
        }
        if (t.contains("numeric") || t.contains("decimal")) {
            if (size > 0 && decimalDigits > 0) {
                return "numeric(" + size + "," + decimalDigits + ")";
            }
            if (size > 0) {
                return "numeric(" + size + ")";
            }
        }
        return t;
    }
}
