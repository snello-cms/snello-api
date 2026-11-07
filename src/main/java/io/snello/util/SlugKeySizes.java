package io.snello.util;

import io.snello.model.FieldDefinition;
import io.snello.model.Metadata;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * VARCHAR length for generated {@code uuid}/{@code slug} key columns.
 * For {@code slug} keys, grows to at least the slug-source field capacity when that field is wider
 * than {@link #DEFAULT_KEY_VARCHAR}.
 */
public final class SlugKeySizes {

    public static final int DEFAULT_KEY_VARCHAR = 50;
    /** Cap for slug keys sourced from unbounded text fields (TEXT is not a practical PK). */
    public static final int LONG_TEXT_KEY_VARCHAR = 2048;

    private static final Pattern VARCHAR_LEN =
            Pattern.compile("(?i)varchar(?:_ignorecase)?\\s*\\(\\s*(\\d+)\\s*\\)");

    private SlugKeySizes() {
    }

    public static int resolveKeyVarcharLength(Metadata metadata, List<FieldDefinition> fields) {
        int length = DEFAULT_KEY_VARCHAR;
        if (metadata == null || !"slug".equals(metadata.table_key_type)) {
            return length;
        }
        String source = metadata.table_key_addition;
        if (source == null || source.isBlank() || fields == null) {
            return length;
        }
        for (FieldDefinition field : fields) {
            if (field == null || field.name == null || !source.equalsIgnoreCase(field.name)) {
                continue;
            }
            int capacity = varcharCapacity(field);
            if (capacity > length) {
                length = capacity;
            }
            break;
        }
        return length;
    }

    public static int varcharCapacity(FieldDefinition field) {
        if (field == null) {
            return -1;
        }
        if (field.sql_definition != null && !field.sql_definition.isBlank()) {
            Matcher m = VARCHAR_LEN.matcher(field.sql_definition);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        }
        return varcharCapacity(field.type, field.input_type);
    }

    /**
     * Same widths used by dialect {@code *FieldDefinitionUtils} for CREATE TABLE columns.
     */
    public static int varcharCapacity(String type, String inputType) {
        if (type == null) {
            return -1;
        }
        String t = type.trim().toLowerCase(Locale.ROOT);
        String input = inputType == null ? null : inputType.trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "input" -> {
                if (input == null || input.equals("text") || input.equals("password") || input.equals("email")) {
                    yield 200;
                }
                yield -1;
            }
            case "gmappath", "gmaplocation", "monaco", "textarea", "tinymce" -> LONG_TEXT_KEY_VARCHAR;
            case "select", "media", "image", "tags", "multijoin", "multiselect", "multilookup",
                 "realtionship", "realtionships", "relationship", "relationships", "join", "lookup" -> 2048;
            default -> -1;
        };
    }
}
