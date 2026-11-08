package io.snello.model;

import java.util.Map;

/**
 * Typed reads from JDBC/REST {@code Map} rows. No reflection — only {@code map.get} + instanceof.
 */
public final class MapReads {

    private MapReads() {
    }

    public static String str(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value instanceof String s ? s : null;
    }

    public static boolean bool(Map<String, Object> map, String key, boolean defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Object value = map.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        return defaultValue;
    }

    public static int intVal(Map<String, Object> map, String key, int defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        return defaultValue;
    }

    public static Integer integer(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value instanceof Number n ? n.intValue() : null;
    }

    /** String column, or {@code String.valueOf} for timestamps / numbers coming from JDBC. */
    public static String asString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        return value instanceof String s ? s : String.valueOf(value);
    }

    public static long longVal(Map<String, Object> map, String key, long defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Object value = map.get(key);
        return value instanceof Number n ? n.longValue() : defaultValue;
    }
}
