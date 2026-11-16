package io.snello.test.util;

import io.snello.util.ParamUtils;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParamUtilsFilterGroupsTest {

    @Test
    void unprefixedParamsStayAnd() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("a", List.of("1"));
        params.put("b", List.of("2"));

        StringBuffer where = new StringBuffer();
        List<Object> in = new ArrayList<>();
        ParamUtils.where(params, where, in);

        assertEquals("a= ? AND b= ?", compact(where.toString()));
        assertEquals(List.of("1", "2"), in);
    }

    @Test
    void kayaksShapedOrGroups() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("or1.length_gt", List.of("600"));
        params.put("or1.width_lt", List.of("41"));
        params.put("or2.level", List.of("advanced", "intermediate"));

        StringBuffer where = new StringBuffer();
        List<Object> in = new ArrayList<>();
        ParamUtils.where(params, where, in);

        assertEquals("( length> ? OR width< ? ) AND ( level= ? OR level= ? )", compact(where.toString()));
        assertEquals(List.of("600", "41", "advanced", "intermediate"), in);
    }

    @Test
    void andGroupWrapsWithAndInside() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("and1.hull", List.of("kayak"));
        params.put("and1.len_gt", List.of("4"));

        StringBuffer where = new StringBuffer();
        List<Object> in = new ArrayList<>();
        ParamUtils.where(params, where, in);

        assertEquals("( hull= ? AND len> ? )", compact(where.toString()));
        assertEquals(List.of("kayak", "4"), in);
    }

    @Test
    void defaultAndPlusOrGroup() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("active", List.of("true"));
        params.put("or1.hull", List.of("kayak", "canoe"));
        params.put("or1.len_gt", List.of("5"));

        StringBuffer where = new StringBuffer();
        List<Object> in = new ArrayList<>();
        ParamUtils.where(params, where, in);

        assertEquals("active= ? AND ( hull= ? OR hull= ? OR len> ? )", compact(where.toString()));
        assertEquals(List.of("true", "kayak", "canoe", "5"), in);
    }

    @Test
    void nestedGroupPrefixIsRejected() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("or1.and1.length_gt", List.of("600"));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> ParamUtils.where(params, new StringBuffer(), new ArrayList<>()));
        assertTrue(ex.getMessage().contains("invalid query parameter"));
    }

    @Test
    void invalidGroupIdZeroIsRejected() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("or0.length_gt", List.of("600"));

        assertThrows(BadRequestException.class,
                () -> ParamUtils.where(params, new StringBuffer(), new ArrayList<>()));
    }

    @Test
    void injectionInGroupedFieldIsRejected() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("or1.1=1 OR username", List.of("x"));

        assertThrows(BadRequestException.class,
                () -> ParamUtils.where(params, new StringBuffer(), new ArrayList<>()));
    }

    private static String compact(String sql) {
        return sql.trim().replaceAll("\\s+", " ");
    }
}
