package io.snello.model.enums;

import java.util.Locale;

/**
 * Action {@code phase} values. Interceptor hooks use {@link #PRE}/{@link #POST};
 * {@link #SCHEDULED} is the synthetic phase for timer-driven jobs.
 */
public enum ActionPhase {
    PRE("PRE"),
    POST("POST"),
    SCHEDULED("SCHEDULED");

    private final String db;

    ActionPhase(String db) {
        this.db = db;
    }

    public String dbValue() {
        return db;
    }

    public static ActionPhase from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT);
        for (ActionPhase t : values()) {
            if (t.db.equals(key)) {
                return t;
            }
        }
        return null;
    }
}
