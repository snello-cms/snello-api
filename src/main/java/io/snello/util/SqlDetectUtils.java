package io.snello.util;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;

public class SqlDetectUtils {

    public static String detectSqlSort(String sort) {
        if (sort == null || sort.isEmpty()) {
            return sort;
        }
        try {
            Statement select = (Statement) CCJSqlParserUtil.parse(sort);
            return null;
        } catch (Exception e) {

        }
        return sort;
    }

}
