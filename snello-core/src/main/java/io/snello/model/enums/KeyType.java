package io.snello.model.enums;

import java.util.Locale;

/**
 * Metadata {@code table_key_type} / generation hints stored as DB strings.
 */
public enum KeyType {
    UUID("uuid"),
    VARCHAR("varchar(255)"),
    AUTO("auto"),
    SLUG("slug");

    private final String db;

    KeyType(String db) {
        this.db = db;
    }

    public String dbValue() {
        return db;
    }

    public static KeyType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        for (KeyType t : values()) {
            if (t.db.equals(key)) {
                return t;
            }
        }
        if (key.startsWith("varchar")) {
            return VARCHAR;
        }
        return null;
    }
}
