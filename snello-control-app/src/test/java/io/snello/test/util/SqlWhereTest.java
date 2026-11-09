package io.snello.test.util;

import io.snello.util.SqlWhere;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlWhereTest {

    @Test
    void noWhereStartsOne() {
        assertEquals(" WHERE ", SqlWhere.joiner("SELECT * FROM t"));
    }

    @Test
    void topLevelWhereIsContinuedWithAnd() {
        assertEquals(" AND ", SqlWhere.joiner("SELECT * FROM t WHERE a = 1"));
        assertEquals(" AND ", SqlWhere.joiner("select * from t where a = 1"));
    }

    @Test
    void whereInsideSubqueryOrLiteralDoesNotCount() {
        assertEquals(" WHERE ", SqlWhere.joiner("SELECT * FROM (SELECT * FROM t WHERE a = 1) q"));
        assertEquals(" WHERE ", SqlWhere.joiner("SELECT 'where' AS w FROM t"));
    }

    @Test
    void danglingKeywordNeedsNoJoiner() {
        assertEquals(" ", SqlWhere.joiner("SELECT * FROM t WHERE "));
        assertEquals(" ", SqlWhere.joiner("SELECT * FROM t WHERE a = 1 AND"));
    }
}
