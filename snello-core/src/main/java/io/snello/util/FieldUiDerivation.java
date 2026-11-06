package io.snello.util;

import java.util.List;
import java.util.Locale;

/**
 * Derives the UI facet of a field from its AI / query facets, so an AI author only states what a field
 * <em>means</em> ({@code semantic_type}) and the widget + column type follow.
 * <p>
 * This matters beyond the SPA: {@link ColumnSpecs} resolves the SQL column kind from the UI {@code type} /
 * {@code input_type} first, so a correct UI facet is what makes {@code CREATE TABLE} right.
 */
public final class FieldUiDerivation {

    /** Vocabulary accepted in {@code ai.semantic_type}. */
    public static final List<String> SEMANTIC_TYPES = List.of(
            "text", "long_text", "html", "integer", "decimal", "money", "boolean",
            "date", "datetime", "time", "email", "password", "url",
            "enum", "image", "media", "tags", "reference", "references");

    private FieldUiDerivation() {
    }

    /** Best-effort semantic type from a SQL type ({@code varchar(80)}, {@code numeric(10,2)}, ...); null if unknown. */
    public static String semanticFromSqlType(String sqlType) {
        if (sqlType == null || sqlType.isBlank()) {
            return null;
        }
        String t = sqlType.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("varchar") || t.startsWith("char") || t.startsWith("character varying")) return "text";
        if (t.equals("text") || t.startsWith("clob") || t.startsWith("longtext") || t.startsWith("mediumtext")) return "long_text";
        if (t.startsWith("bool")) return "boolean";
        if (t.startsWith("timestamp") || t.startsWith("datetime")) return "datetime";
        if (t.equals("date")) return "date";
        if (t.startsWith("time")) return "time";
        if (t.startsWith("int") || t.startsWith("bigint") || t.startsWith("smallint") || t.equals("serial")) return "integer";
        if (t.startsWith("numeric") || t.startsWith("decimal")) return t.contains(",") ? "decimal" : "integer";
        if (t.startsWith("double") || t.startsWith("float") || t.startsWith("real")) return "decimal";
        return null;
    }

    /**
     * UI {@code {type, input_type}} for a semantic type ({@code input_type} may be null).
     *
     * @throws IllegalArgumentException for a value outside {@link #SEMANTIC_TYPES}
     */
    public static String[] uiTypeFor(String semanticType) {
        return switch (semanticType) {
            case "text", "url" -> new String[]{"input", "text"};
            case "long_text" -> new String[]{"textarea", null};
            case "html" -> new String[]{"tinymce", null};
            case "integer" -> new String[]{"input", "number"};
            case "decimal", "money" -> new String[]{"input", "decimal"};
            case "boolean" -> new String[]{"checkbox", null};
            case "date" -> new String[]{"date", null};
            case "datetime" -> new String[]{"datetime", null};
            case "time" -> new String[]{"time", null};
            case "email" -> new String[]{"input", "email"};
            case "password" -> new String[]{"input", "password"};
            case "enum" -> new String[]{"select", null};
            case "image" -> new String[]{"image", null};
            case "media" -> new String[]{"media", null};
            case "tags" -> new String[]{"tags", null};
            case "reference" -> new String[]{"join", null};
            case "references" -> new String[]{"multijoin", null};
            default -> throw new IllegalArgumentException("unknown semantic_type '" + semanticType
                    + "', allowed: " + String.join(", ", SEMANTIC_TYPES));
        };
    }

    /** Free-text kinds are searched with {@code _contains}; everything else by equality. */
    public static boolean textLike(String semanticType) {
        return switch (semanticType == null ? "" : semanticType) {
            case "text", "long_text", "html", "email", "url", "tags" -> true;
            default -> false;
        };
    }

    /** Long / binary content is not shown in list views by default. */
    public static boolean shownInListByDefault(String semanticType) {
        return switch (semanticType == null ? "" : semanticType) {
            case "long_text", "html", "media", "image", "password", "references" -> false;
            default -> true;
        };
    }

    /** {@code unit_price} → {@code Unit price}. */
    public static String label(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        String spaced = name.replace('_', ' ').trim();
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
