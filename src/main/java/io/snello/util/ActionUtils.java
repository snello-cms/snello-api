package io.snello.util;

import java.util.Map;

public class ActionUtils {

    private static final String PERSIST = "PERSIST";
    private static final String MERGE = "MERGE";
    private static final String DELETE = "DELETE";

    public static String actionKey(String metadata_name, String condition) {
        return metadata_name + "_" + condition;
    }

    public static String conditionByMethod(String methodName) {
        if ("create".equals(methodName)) {
            return PERSIST;
        }
        if ("update".equals(methodName)) {
            return MERGE;
        }
        if ("delete".equals(methodName)) {
            return DELETE;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractPayloadMap(Object[] params, Object ret) {
        if (params.length > 2 && params[2] instanceof Map<?, ?>) {
            return (Map<String, Object>) params[2];
        }
        if (ret instanceof Map<?, ?>) {
            return (Map<String, Object>) ret;
        }
        return null;
    }
}