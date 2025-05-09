package io.snello;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;

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
            Statement select = (Statement) CCJSqlParserUtil.parse(sql);
            return true;
        } catch (Exception e) {

        }
        return false;
    }

}
