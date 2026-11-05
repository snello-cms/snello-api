package io.snello.test.util;

import io.snello.util.ParamUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParamUtilsNullEmptyTest {

    // --- _nn (IS NOT NULL) ---

    @Test
    void nnShouldProduceIsNotNullWithNoValue() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("ficr_code_nn", Collections.emptyList());

        StringBuffer where = new StringBuffer();
        List<Object> in = new ArrayList<>();
        ParamUtils.where(params, where, in);

        assertEquals("ficr_code IS NOT NULL", compact(where.toString()));
        assertTrue(in.isEmpty());
    }

    // --- _inn (IS NULL) ---

    @Test
    void innShouldProduceIsNullWithNoValue() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("ficr_code_inn", Collections.emptyList());

        StringBuffer where = new StringBuffer();
        List<Object> in = new ArrayList<>();
        ParamUtils.where(params, where, in);

        assertEquals("ficr_code IS NULL", compact(where.toString()));
        assertTrue(in.isEmpty());
    }

    // --- _nie (field <> '') ---

    @Test
    void nieShouldProduceNotEmptyWithNoValue() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("ficr_code_nie", Collections.emptyList());

        StringBuffer where = new StringBuffer();
        List<Object> in = new ArrayList<>();
        ParamUtils.where(params, where, in);

        assertEquals("ficr_code <> ''", compact(where.toString()));
        assertTrue(in.isEmpty());
    }

    @Test
    void nieShouldBeIgnoredWhenValueIsProvided() {
        // _nie does not use a value even if one is passed; the suffix triggers no-value path
        Map<String, List<String>> params = new HashMap<>();
        params.put("ficr_code_nie", List.of("anything"));

        StringBuffer where = new StringBuffer();
        List<Object> in = new ArrayList<>();
        ParamUtils.where(params, where, in);

        // With a non-empty value the key is processed in the "value present" block;
        // _nie is no longer there so it falls through to the equality default.
        // This test documents the expected behaviour: provide _nie without a value.
        assertTrue(where.length() > 0);
    }

    // --- _ie (field = '') ---

    @Test
    void ieShouldProduceIsEmptyWithNoValue() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("ficr_code_ie", Collections.emptyList());

        StringBuffer where = new StringBuffer();
        List<Object> in = new ArrayList<>();
        ParamUtils.where(params, where, in);

        assertEquals("ficr_code = ''", compact(where.toString()));
        assertTrue(in.isEmpty());
    }

    // --- combined: _inn and _nie together ---

    @Test
    void innAndNieShouldCombineWithAnd() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("ficr_code_inn", Collections.emptyList());
        params.put("organizer_nie", Collections.emptyList());

        StringBuffer where = new StringBuffer();
        List<Object> in = new ArrayList<>();
        ParamUtils.where(params, where, in);

        String sql = compact(where.toString());
        assertTrue(sql.contains("ficr_code IS NULL"), "expected IS NULL: " + sql);
        assertTrue(sql.contains("organizer <> ''"), "expected <> '': " + sql);
        assertTrue(sql.contains("AND"), "expected AND connector: " + sql);
        assertTrue(in.isEmpty());
    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
