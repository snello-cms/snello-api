package io.snello.service;

import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns values coming out of a Nashorn script into plain Java (Map / List / String / Number / Boolean).
 * <p>
 * Nashorn hands JS objects to Java as {@link ScriptObjectMirror}, and that includes arrays, which are Maps keyed
 * "0", "1", ... Serialized as is, {@code {items: [1, 2]}} becomes {@code {"items":{"0":1,"1":2}}}.
 */
public final class ScriptValues {

    private ScriptValues() {
    }

    public static Object toJava(Object value) {
        if (value instanceof ScriptObjectMirror m) {
            if (m.isArray()) {
                List<Object> list = new ArrayList<>(m.size());
                for (Object v : m.values()) {
                    list.add(toJava(v));
                }
                return list;
            }
            if ("Date".equals(m.getClassName())) {
                return m.callMember("toISOString");
            }
            if (m.isFunction()) {
                return null; // JSON.stringify drops functions too
            }
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : m.entrySet()) {
                map.put(e.getKey(), toJava(e.getValue()));
            }
            return map;
        }
        if (value instanceof Map<?, ?> source) {
            Map<Object, Object> map = new LinkedHashMap<>();
            source.forEach((k, v) -> map.put(k, toJava(v)));
            return map;
        }
        if (value instanceof Collection<?> source) {
            List<Object> list = new ArrayList<>(source.size());
            source.forEach(v -> list.add(toJava(v)));
            return list;
        }
        return value;
    }
}
