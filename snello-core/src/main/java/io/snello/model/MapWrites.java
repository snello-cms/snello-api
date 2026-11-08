package io.snello.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed writes into JDBC/REST {@code Map} rows. No reflection.
 */
public final class MapWrites {

    private MapWrites() {
    }

    public static Map<String, Object> map() {
        return new LinkedHashMap<>();
    }

    public static void put(Map<String, Object> map, String key, Object value) {
        if (map != null && value != null) {
            map.put(key, value);
        }
    }

    public static void putBool(Map<String, Object> map, String key, boolean value) {
        if (map != null) {
            map.put(key, value);
        }
    }

    public static void putInt(Map<String, Object> map, String key, int value) {
        if (map != null) {
            map.put(key, value);
        }
    }
}
