package io.snello.util;

import io.snello.model.FieldDefinition;

/**
 * Utility class for mapping FieldDefinition types to unified, simplified types for AI context.
 * Normalizes: string, number, decimal, date, datetime
 * For join fields, returns descriptive labels with foreign key information.
 */
public class FieldTypeUtils {

    private FieldTypeUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Maps FieldDefinition type and input_type to a unified, simplified type.
     * 
     * @param fd the FieldDefinition to process
     * @return unified type string (string, number, decimal, date, datetime, boolean, etc.)
     *         or null if the field definition is null or has no type
     */
    public static String getUnifiedFieldType(FieldDefinition fd) {
        if (fd == null || fd.type == null) {
            return null;
        }

        String type = fd.type;

        if ("input".equals(type)) {
            if (fd.input_type == null) {
                return "string";
            }
            switch (fd.input_type) {
                case "text":
                case "password":
                case "email":
                    return "string";
                case "number":
                    return "number";
                case "decimal":
                    return "decimal";
                default:
                    return "string";
            }
        }

        if ("date".equals(type)) {
            return "date";
        }
        if ("datetime".equals(type)) {
            return "datetime";
        }
        if ("time".equals(type)) {
            return "datetime";
        }

        if ("number".equals(type)) {
            return "number";
        }
        if ("decimal".equals(type)) {
            return "decimal";
        }

        if ("join".equals(type) || "lookup".equals(type)) {
            String desc = fd.join_table_name != null ? " (join entity: " + fd.join_table_name + ")" : "";
            return "text" + desc;
        }
        if (FieldDefinitionUtils.isMultiJoinType(type)) {
            String desc = fd.join_table_name != null ? " (multijoin: comma-separated uuids from " + fd.join_table_name + ")" : " (multijoin)";
            return "text" + desc;
        }

        // For all other types (textarea, checkbox, select, media, image, tags, etc.), 
        // return "string" or descriptive type-based label
        if ("textarea".equals(type) || "tinymce".equals(type) || "monaco".equals(type) || "gmaplocation".equals(type) || "gmappath".equals(type)) {
            return "string";
        }
        if ("checkbox".equals(type) || "passivation".equals(type)) {
            return "boolean";
        }
        if ("select".equals(type) || "radiobutton".equals(type)) {
            if (fd.options == null || fd.options.isBlank()) {
                return "string (enumerated)";
            }
            String normalizedOptions = fd.options
                    .replace("\r\n", "\n")
                    .replace('\r', '\n')
                    .replace('\n', ',')
                    .replaceAll("\\s*,\\s*", ", ")
                    .replaceAll("^,\\s*", "")
                    .replaceAll("\\s*,$", "")
                    .trim();
            return "string (enumerated) of values " + normalizedOptions;
        }
        if ("media".equals(type) || "image".equals(type) || "tags".equals(type)) {
            return "string";
        }

        // Default fallback
        return "string";
    }
}
