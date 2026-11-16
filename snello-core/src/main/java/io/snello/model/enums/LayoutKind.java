package io.snello.model.enums;

import java.util.Locale;

/**
 * {@code json_data.kind} values for layouts, stored as lowercase strings in DB JSON.
 */
public enum LayoutKind {
    LIST("list"),
    WIZARD("wizard");

    private final String db;

    LayoutKind(String db) {
        this.db = db;
    }

    public String dbValue() {
        return db;
    }

    public static LayoutKind from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        for (LayoutKind t : values()) {
            if (t.db.equals(key)) {
                return t;
            }
        }
        return null;
    }
}
