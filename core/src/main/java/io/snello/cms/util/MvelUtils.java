package io.snello.util;

import org.mvel2.MVEL;

import java.util.Map;

public class MvelUtils {
    public static void evaluate(String condition, Map<String, Object> map) {
        MVEL.eval(condition, map, Boolean.class);

    }
}
