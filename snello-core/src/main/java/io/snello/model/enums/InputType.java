package io.snello.model.enums;

import java.util.Locale;

/**
 * HTML / widget {@code fielddefinitions.input_type} values.
 */
public enum InputType {
    TEXT("text"),
    PASSWORD("password"),
    EMAIL("email"),
    NUMBER("number"),
    DECIMAL("decimal"),
    RADIO("radio"),
    CHECKBOX("checkbox"),
    COLOR("color"),
    DATE("date"),
    DATETIME_LOCAL("datetime-local"),
    MONTH("month"),
    RANGE("range"),
    SEARCH("search"),
    TEL("tel"),
    TIME("time"),
    URL("url"),
    WEEK("week"),
    PASSIVATION("passivation");

    private final String db;

    InputType(String db) {
        this.db = db;
    }

    public String dbValue() {
        return db;
    }

    public static InputType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        for (InputType t : values()) {
            if (t.db.equals(key)) {
                return t;
            }
        }
        return null;
    }
}
