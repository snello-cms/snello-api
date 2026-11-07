package io.snello.util;

import io.snello.model.enums.InputType;
import io.snello.model.enums.SemanticType;
import io.snello.model.enums.WidgetType;

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
    public static final List<String> SEMANTIC_TYPES = SemanticType.NAMES;

    private FieldUiDerivation() {
    }

    /** Best-effort semantic type from a SQL type ({@code varchar(80)}, {@code numeric(10,2)}, ...); null if unknown. */
    public static String semanticFromSqlType(String sqlType) {
        SemanticType semantic = semanticTypeFromSqlType(sqlType);
        return semantic == null ? null : semantic.dbValue();
    }

    public static SemanticType semanticTypeFromSqlType(String sqlType) {
        if (sqlType == null || sqlType.isBlank()) {
            return null;
        }
        String t = sqlType.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("varchar") || t.startsWith("char") || t.startsWith("character varying")) return SemanticType.TEXT;
        if (t.equals("text") || t.startsWith("clob") || t.startsWith("longtext") || t.startsWith("mediumtext")) return SemanticType.LONG_TEXT;
        if (t.startsWith("bool")) return SemanticType.BOOLEAN;
        if (t.startsWith("timestamp") || t.startsWith("datetime")) return SemanticType.DATETIME;
        if (t.equals("date")) return SemanticType.DATE;
        if (t.startsWith("time")) return SemanticType.TIME;
        if (t.startsWith("int") || t.startsWith("bigint") || t.startsWith("smallint") || t.equals("serial")) return SemanticType.INTEGER;
        if (t.startsWith("numeric") || t.startsWith("decimal")) return t.contains(",") ? SemanticType.DECIMAL : SemanticType.INTEGER;
        if (t.startsWith("double") || t.startsWith("float") || t.startsWith("real")) return SemanticType.DECIMAL;
        return null;
    }

    /**
     * UI {@code {type, input_type}} for a semantic type ({@code input_type} may be null).
     *
     * @throws IllegalArgumentException for a value outside {@link #SEMANTIC_TYPES}
     */
    public static String[] uiTypeFor(String semanticType) {
        SemanticType semantic = SemanticType.from(semanticType);
        if (semantic == null) {
            throw new IllegalArgumentException("unknown semantic_type '" + semanticType
                    + "', allowed: " + String.join(", ", SEMANTIC_TYPES));
        }
        return switch (semantic) {
            case TEXT, URL -> new String[]{WidgetType.INPUT.dbValue(), InputType.TEXT.dbValue()};
            case LONG_TEXT -> new String[]{WidgetType.TEXTAREA.dbValue(), null};
            case HTML -> new String[]{WidgetType.TINYMCE.dbValue(), null};
            case INTEGER -> new String[]{WidgetType.INPUT.dbValue(), InputType.NUMBER.dbValue()};
            case DECIMAL, MONEY -> new String[]{WidgetType.INPUT.dbValue(), InputType.DECIMAL.dbValue()};
            case BOOLEAN -> new String[]{WidgetType.CHECKBOX.dbValue(), null};
            case DATE -> new String[]{WidgetType.DATE.dbValue(), null};
            case DATETIME -> new String[]{WidgetType.DATETIME.dbValue(), null};
            case TIME -> new String[]{WidgetType.TIME.dbValue(), null};
            case EMAIL -> new String[]{WidgetType.INPUT.dbValue(), InputType.EMAIL.dbValue()};
            case PASSWORD -> new String[]{WidgetType.INPUT.dbValue(), InputType.PASSWORD.dbValue()};
            case ENUM -> new String[]{WidgetType.SELECT.dbValue(), null};
            case IMAGE -> new String[]{WidgetType.IMAGE.dbValue(), null};
            case MEDIA -> new String[]{WidgetType.MEDIA.dbValue(), null};
            case TAGS -> new String[]{WidgetType.TAGS.dbValue(), null};
            case REFERENCE -> new String[]{WidgetType.JOIN.dbValue(), null};
            case REFERENCES -> new String[]{WidgetType.MULTIJOIN.dbValue(), null};
        };
    }

    /** Free-text kinds are searched with {@code _contains}; everything else by equality. */
    public static boolean textLike(String semanticType) {
        SemanticType semantic = SemanticType.from(semanticType);
        return semantic == SemanticType.TEXT || semantic == SemanticType.LONG_TEXT || semantic == SemanticType.HTML
                || semantic == SemanticType.EMAIL || semantic == SemanticType.URL || semantic == SemanticType.TAGS;
    }

    /** Long / binary content is not shown in list views by default. */
    public static boolean shownInListByDefault(String semanticType) {
        SemanticType semantic = SemanticType.from(semanticType);
        return semantic != SemanticType.LONG_TEXT && semantic != SemanticType.HTML && semantic != SemanticType.MEDIA
                && semantic != SemanticType.IMAGE && semantic != SemanticType.PASSWORD && semantic != SemanticType.REFERENCES;
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
