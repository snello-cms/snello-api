package io.snello.util;

public class FieldDefinitionUtils {

    private FieldDefinitionUtils() {
        // Utility class
    }

    public static boolean isMultiJoinType(String type) {
        return "multijoin".equals(type) || "multiselect".equals(type) || "multilookup".equals(type);
    }

    public static String multijoin(String table, String joinTable, String tableUuid, String joinTableUuid) {

        return "";
    }
}
