package io.snello.test.util;

import io.snello.util.ParamUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParamUtilsWhereInTest {

    @Test
    void shouldBuildInClauseWithMultipleValues() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("status_in", List.of("draft,published,archived"));

        StringBuffer where = new StringBuffer();
        List<Object> in = new ArrayList<>();

        ParamUtils.where(params, where, in);

        assertEquals("( status IN (?,?,?) )", compact(where.toString()));
        assertEquals(List.of("draft", "published", "archived"), in);
    }

    @Test
    void shouldAppendAndAndSinglePlaceholderWhenWhereAlreadyHasConditions() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("category_in", List.of(" news "));

        StringBuffer where = new StringBuffer("enabled = ? ");
        List<Object> in = new ArrayList<>();

        ParamUtils.where(params, where, in);

        assertEquals("enabled = ? AND ( category IN ( ? ) )", compact(where.toString()));
        assertEquals(List.of("news"), in);
    }

    @Test
    void shouldTrimWhitespaceWhenSplittingCommaSeparatedValues() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("status_in", List.of("draft, published, archived"));

        StringBuffer where = new StringBuffer();
        List<Object> in = new ArrayList<>();

        ParamUtils.where(params, where, in);

        assertEquals("( status IN (?,?,?) )", compact(where.toString()));
        assertEquals(List.of("draft", "published", "archived"), in);
    }

    @Test
    void shouldTreatIcontainsAsCaseInsensitiveLike() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("caption_icontains", List.of("bike"));

        StringBuffer where = new StringBuffer();
        List<Object> in = new ArrayList<>();

        ParamUtils.where(params, where, in);

        assertEquals("caption ILIKE ?", compact(where.toString()));
        assertEquals(List.of("%bike%"), in);
    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
