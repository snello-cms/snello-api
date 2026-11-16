package io.snello.model.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Layout {@code page_type} values, stored as lowercase strings in DB.
 */
public enum LayoutPageType {
    LIST("list"),
    EDIT("edit"),
    VIEW("view");

    private final String db;

    LayoutPageType(String db) {
        this.db = db;
    }

    public String dbValue() {
        return db;
    }

    public static LayoutPageType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        for (LayoutPageType t : values()) {
            if (t.db.equals(key)) {
                return t;
            }
        }
        return null;
    }

    /** All allowed DB literals, for error messages. */
    public static Set<String> dbValues() {
        return Arrays.stream(values()).map(LayoutPageType::dbValue).collect(Collectors.toUnmodifiableSet());
    }
}
