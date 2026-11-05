package io.snello.test.util;

import io.snello.util.ParamUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParamUtilsDirectionalLikeTest {

    @ParameterizedTest
    @CsvSource({
            "_rlike, ms.ocea, ms.ocea%, true",
            "_rlike, MS.OCEA, MS.OCEA%, true",
            "_rlike, ms.oceanpaddle, ms.oceanpaddle%, true",
            "_rlike, oceanpaddle, oceanpaddle%, false",
            "_llike, oceanpaddle, %oceanpaddle, true",
            "_llike, OCEANPADDLE, %OCEANPADDLE, true",
            "_llike, ms.oceanpaddle, %ms.oceanpaddle, true",
            "_llike, ms.ocea, %ms.ocea, false"
    })
    void shouldMatchCaseInsensitivelyWithoutChangingWildcardDirection(
            String suffix, String value, String pattern, boolean expectedMatch) throws Exception {
        StringBuffer where = new StringBuffer();
        List<Object> in = new ArrayList<>();

        ParamUtils.where(Map.of("account" + suffix, List.of(value)), where, in);

        assertEquals("lower(account) LIKE lower(?)", compact(where.toString()));
        assertEquals(List.of(pattern), in);

        try (var connection = DriverManager.getConnection("jdbc:h2:mem:");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE instagram_accounts (account VARCHAR(100))");
            statement.execute("INSERT INTO instagram_accounts VALUES ('ms.oceanpaddle'), ('MS.OCEANPADDLE')");
            try (var query = connection.prepareStatement(
                    "SELECT COUNT(*) FROM instagram_accounts WHERE " + where)) {
                query.setString(1, pattern);
                try (var result = query.executeQuery()) {
                    result.next();
                    assertEquals(expectedMatch ? 2 : 0, result.getInt(1));
                }
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"_rlike, MS.OCEA%", "_llike, %MS.OCEA"})
    void shouldAppendToExistingConditions(String suffix, String pattern) {
        StringBuffer where = new StringBuffer("active = ? ");
        List<Object> in = new ArrayList<>(List.of(true));

        ParamUtils.where(Map.of("account" + suffix, List.of("MS.OCEA")), where, in);

        assertEquals("active = ? AND lower(account) LIKE lower(?)",
                compact(where.toString()));
        assertEquals(List.of(true, pattern), in);
    }

    @Test
    void shouldPreserveOtherLikeOperators() {
        for (String suffix : List.of("_like", "_contains", "_ilike", "_icontains")) {
            StringBuffer where = new StringBuffer();
            List<Object> in = new ArrayList<>();

            ParamUtils.where(Map.of("account" + suffix, List.of("MS.OCEA")), where, in);

            String operator = suffix.startsWith("_i") ? "ILIKE" : "LIKE";
            assertEquals("account " + operator + " ?", compact(where.toString()));
            assertEquals(List.of("%MS.OCEA%"), in);
        }

    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
