package io.snello.model.enums;

import java.util.Locale;

/**
 * UI widget {@code fielddefinitions.type} values, stored as lowercase strings in DB.
 */
public enum WidgetType {
    INPUT("input"),
    BUTTON("button"),
    SELECT("select"),
    DATE("date"),
    DATETIME("datetime"),
    TIME("time"),
    RADIOBUTTON("radiobutton"),
    CHECKBOX("checkbox"),
    PASSIVATION("passivation"),
    JOIN("join"),
    LOOKUP("lookup"),
    MULTIJOIN("multijoin"),
    MULTILOOKUP("multilookup"),
    MULTISELECT("multiselect"),
    TEXTAREA("textarea"),
    TINYMCE("tinymce"),
    MONACO("monaco"),
    IMAGE("image"),
    MEDIA("media"),
    TAGS("tags"),
    GMAPPATH("gmappath"),
    GMAPLOCATION("gmaplocation"),
    /** Legacy typos kept for dump compatibility. */
    REALTIONSHIP("realtionship"),
    REALTIONSHIPS("realtionships"),
    RELATIONSHIP("relationship"),
    RELATIONSHIPS("relationships");

    private final String db;

    WidgetType(String db) {
        this.db = db;
    }

    public String dbValue() {
        return db;
    }

    /** {@code null}/blank → {@code null}; unknown literals stay usable via {@link #dbValue()} round-trip only if known. */
    public static WidgetType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        for (WidgetType t : values()) {
            if (t.db.equals(key)) {
                return t;
            }
        }
        return null;
    }

    public boolean isMultiJoin() {
        return this == MULTIJOIN || this == MULTISELECT || this == MULTILOOKUP;
    }

    public boolean isJoinLike() {
        return this == JOIN || this == LOOKUP || isMultiJoin()
                || this == REALTIONSHIP || this == REALTIONSHIPS
                || this == RELATIONSHIP || this == RELATIONSHIPS;
    }
}
