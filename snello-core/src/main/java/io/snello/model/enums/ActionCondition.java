package io.snello.model.enums;

import java.util.Locale;

/**
 * Action {@code condition} values tied to {@code ApiService} write methods.
 */
public enum ActionCondition {
    PERSIST("PERSIST"),
    MERGE("MERGE"),
    DELETE("DELETE");

    private final String db;

    ActionCondition(String db) {
        this.db = db;
    }

    public String dbValue() {
        return db;
    }

    public static ActionCondition from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT);
        for (ActionCondition t : values()) {
            if (t.db.equals(key)) {
                return t;
            }
        }
        return null;
    }

    /** Maps {@code ApiService} method names ({@code create}/{@code merge}/{@code update}/{@code delete}). */
    public static ActionCondition fromMethod(String methodName) {
        if (methodName == null) {
            return null;
        }
        return switch (methodName) {
            case "create" -> PERSIST;
            case "merge", "update" -> MERGE;
            case "delete" -> DELETE;
            default -> null;
        };
    }
}
