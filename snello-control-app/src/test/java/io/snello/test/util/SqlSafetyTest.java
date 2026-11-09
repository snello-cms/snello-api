package io.snello.test.util;

import io.snello.util.SqlSafety;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlSafetyTest {

    @Test
    void acceptsPlainSorts() {
        for (String s : new String[]{"name", "name asc", "name:desc", "t.name DESC", "a asc, b:desc", " random ", "", null}) {
            assertDoesNotThrow(() -> SqlSafety.requireSafeSort(s), String.valueOf(s));
        }
    }

    @Test
    void rejectsExpressionSorts() {
        for (String s : new String[]{"(SELECT 1)", "name; DROP TABLE x", "lower(name)", "name desc --", "1/0", "name asc nulls"}) {
            assertThrows(BadRequestException.class, () -> SqlSafety.requireSafeSort(s), s);
        }
    }

    @Test
    void acceptsPlainSelectFields() {
        for (String s : new String[]{"*", "t.*", "uuid", "uuid, name", "name AS n, t.city as c"}) {
            assertDoesNotThrow(() -> SqlSafety.requireSafeSelectFields(s), s);
        }
    }

    @Test
    void rejectsExpressionSelectFields() {
        for (String s : new String[]{"uuid, (SELECT name FROM x) AS leak", "count(*)", "name || 'x'", "uuid FROM x --"}) {
            assertThrows(BadRequestException.class, () -> SqlSafety.requireSafeSelectFields(s), s);
        }
    }

    @Test
    void ownerColumnWithOperatorSuffixIsUnsafe() {
        assertFalse(SqlSafety.isUnsafeOwnerColumn("username"));
        assertFalse(SqlSafety.isUnsafeOwnerColumn("action_username"));
        assertTrue(SqlSafety.isUnsafeOwnerColumn("owner_ne"));
        assertTrue(SqlSafety.isUnsafeOwnerColumn("logged_in"));
        assertTrue(SqlSafety.isUnsafeOwnerColumn("1=1 or x"));
        assertTrue(SqlSafety.isUnsafeOwnerColumn(null));
    }
}
