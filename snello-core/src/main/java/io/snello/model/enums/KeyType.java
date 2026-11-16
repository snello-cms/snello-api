package io.snello.model.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Metadata {@code table_key_type} values, stored as lowercase strings in DB.
 */
public enum KeyType {
    UUID("uuid"),
    SLUG("slug"),
    AUTOINCREMENT("autoincrement"),
    USERDEFINED("userdefined");

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
        return null;
    }

    /** Null/blank → {@link #USERDEFINED} (DDL treats an unknown key as a declared field). */
    public static KeyType fromOrUserDefined(String raw) {
        KeyType type = from(raw);
        return type == null ? USERDEFINED : type;
    }

    public static Set<String> dbValues() {
        return Arrays.stream(values()).map(KeyType::dbValue).collect(Collectors.toUnmodifiableSet());
    }

    public boolean isGenerated() {
        return this != USERDEFINED;
    }
}
