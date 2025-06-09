package io.snello;

import org.junit.Test;

import static io.snello.util.SqlDetectUtils.detectSqlSort;
import static org.junit.Assert.assertFalse;


public class DetectSqlTest {

    @Test
    public void notSql1() {
        String selectSQL = "(SELECT (CASE WHEN (5698=5698) THEN 'ordine ASC' ELSE (SELECT 6357 UNION SELECT 4776) END))";
        assertFalse(isSqlStatement(selectSQL));

    }

    @Test
    public void notSql2() {
        String selectSQL = "(SELECT (CASE WHEN (5698=5698) THEN 'ordine' ASC ELSE (SELECT 6357 UNION SELECT 4776) END))";
        assertFalse(isSqlStatement(selectSQL));

    }

    protected boolean isSqlStatement(String sql) {
        try {
            String result = detectSqlSort(sql);
            if (result != null && !result.isEmpty())
                return true;
        } catch (Exception e) {

        }
        return false;
    }

}
