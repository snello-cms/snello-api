package io.snello.util;

import java.util.Map;

public class ActionUtils {

    private static final String PERSIST = "PERSIST";
    private static final String MERGE = "MERGE";
    private static final String DELETE = "DELETE";
    public static final String PHASE_PRE = "PRE";
    public static final String PHASE_POST = "POST";

    public static String actionKey(String metadata_name, String condition) {
        return actionKey(metadata_name, condition, PHASE_POST);
    }

    public static String actionKey(String metadata_name, String condition, String phase) {
        return metadata_name + "_" + condition + "_" + normalizePhase(phase);
    }

    public static String normalizePhase(String phase) {
        if (PHASE_PRE.equalsIgnoreCase(phase)) {
            return PHASE_PRE;
        }
        return PHASE_POST;
    }

    public static String conditionByMethod(String methodName) {
        if ("create".equals(methodName)) {
            return PERSIST;
        }
        if ("merge".equals(methodName)) {
            return MERGE;
        }
        if ("update".equals(methodName)) {
            return MERGE;
        }
        if ("delete".equals(methodName)) {
            return DELETE;
        }
        return null;
    }

    /**
     * The row payload of an intercepted {@code ApiService} call: the map argument ({@code create(table, map, key)},
     * {@code merge(table, map, id, key)}), else the returned map. It used to look at {@code params[2]} only, which is
     * never the map, so PRE actions (no return value yet) always got {@code map = null}. A PRE script may modify the
     * map: it is the same instance that is then saved.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractPayloadMap(Object[] params, Object ret) {
        for (Object param : params) {
            if (param instanceof Map<?, ?>) {
                return (Map<String, Object>) param;
            }
        }
        if (ret instanceof Map<?, ?>) {
            return (Map<String, Object>) ret;
        }
        return null;
    }

    /**
     * Key value of the row an intercepted call works on: {@code merge(table, map, id, keyColumn)} -> id,
     * {@code delete(table, id, keyColumn)} -> id, {@code create(table, map, keyColumn)} -> map[keyColumn].
     */
    public static Object rowKey(String condition, Object[] params, Map<String, Object> map, String keyColumn) {
        if (MERGE.equals(condition) && params.length > 2 && params[2] instanceof String id) {
            return id;
        }
        if (DELETE.equals(condition) && params.length > 1 && params[1] instanceof String id) {
            return id;
        }
        return map == null || keyColumn == null ? null : map.get(keyColumn);
    }
}