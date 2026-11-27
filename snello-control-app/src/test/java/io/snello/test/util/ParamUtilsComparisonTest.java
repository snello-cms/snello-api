package io.snello.test.util;

import io.snello.util.ParamUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The four comparison suffixes. {@code _gte} used to be written as {@code >}: a range
 * {@code ?day_gte=2026-11-28&day_lte=2026-11-30} silently left the first day out.
 */
class ParamUtilsComparisonTest {

    @Test
    void eachSuffixHasItsOwnOperator() {
        assertEquals("day< ?", whereOf("day_lt"));
        assertEquals("day<= ?", whereOf("day_lte"));
        assertEquals("day> ?", whereOf("day_gt"));
        assertEquals("day>= ?", whereOf("day_gte"));
    }

    @Test
    void gteKeepsItsValue() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("day_gte", List.of("2026-11-28"));
        List<Object> in = new ArrayList<>();

        ParamUtils.where(params, new StringBuffer(), in);

        assertEquals(List.of("2026-11-28"), in);
    }

    private static String whereOf(String key) {
        Map<String, List<String>> params = new HashMap<>();
        params.put(key, List.of("2026-11-28"));
        StringBuffer where = new StringBuffer();
        ParamUtils.where(params, where, new ArrayList<>());
        return where.toString().trim().replaceAll("\\s+", " ");
    }
}
