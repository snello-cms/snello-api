package io.snello.model.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Print template {@code export_type} values, stored as lowercase strings in DB.
 */
public enum PrintExportType {
    PDF("pdf"),
    HTML("html"),
    CSV("csv"),
    XLSX("xlsx");

    private final String db;

    PrintExportType(String db) {
        this.db = db;
    }

    public String dbValue() {
        return db;
    }

    public static PrintExportType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        for (PrintExportType t : values()) {
            if (t.db.equals(key)) {
                return t;
            }
        }
        return null;
    }

    /** All allowed DB literals, for error messages. */
    public static Set<String> dbValues() {
        return Arrays.stream(values()).map(PrintExportType::dbValue).collect(Collectors.toUnmodifiableSet());
    }
}
