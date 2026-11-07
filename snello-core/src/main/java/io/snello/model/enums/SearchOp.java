package io.snello.model.enums;

import java.util.Locale;

/**
 * Field {@code search_condition} / HTTP query-param suffixes used by {@link io.snello.util.ParamUtils}.
 */
public enum SearchOp {
    EQUALS("="),
    NE("_ne"),
    LT("_lt"),
    GT("_gt"),
    LTE("_lte"),
    GTE("_gte"),
    CONTAINS("_contains"),
    NCONTAINS("_ncontains"),
    NN("_nn"),
    NULL("_null");

    private final String db;

    SearchOp(String db) {
        this.db = db;
    }

    public String dbValue() {
        return db;
    }

    public static SearchOp from(String raw) {
        if (raw == null || raw.isBlank()) {
            return EQUALS;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        for (SearchOp op : values()) {
            if (op.db.equals(key) || op.name().equalsIgnoreCase(key)) {
                return op;
            }
        }
        return null;
    }
}
