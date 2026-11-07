package io.snello.model.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * AI {@code semantic_type} vocabulary used to derive UI + SQL column kinds.
 */
public enum SemanticType {
    TEXT("text"),
    LONG_TEXT("long_text"),
    HTML("html"),
    INTEGER("integer"),
    DECIMAL("decimal"),
    MONEY("money"),
    BOOLEAN("boolean"),
    DATE("date"),
    DATETIME("datetime"),
    TIME("time"),
    EMAIL("email"),
    PASSWORD("password"),
    URL("url"),
    ENUM("enum"),
    IMAGE("image"),
    MEDIA("media"),
    TAGS("tags"),
    REFERENCE("reference"),
    REFERENCES("references");

    private final String db;

    SemanticType(String db) {
        this.db = db;
    }

    public String dbValue() {
        return db;
    }

    public static final List<String> NAMES = Arrays.stream(values())
            .map(SemanticType::dbValue)
            .collect(Collectors.toUnmodifiableList());

    public static SemanticType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        for (SemanticType t : values()) {
            if (t.db.equals(key)) {
                return t;
            }
        }
        return null;
    }
}
