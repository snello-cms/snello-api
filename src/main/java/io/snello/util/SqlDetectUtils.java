package io.snello.util;

import java.util.Arrays;

public class SqlDetectUtils {

    public static String detectSqlSort(String sql) {
        if (sql == null)
            return null;
        String[] words = {"=", "select", "case", "union", "from", "dual", "where", "and", "or", "not", "exists", "(", ")", "in", "like", "is", "null", "between", "as", "distinct", "all", "any", "count", "sum", "avg", "min", "max", "group_concat", "having", "order by", "limit", "offset", "CASE", "WHEN", "THEN", "ELSE", "end", "ORDER BY", "ASC", "DESC", "LIMIT", "OFFSET", "GROUP BY", "HAVING", "JOIN", "INNER", "JOIN", "LEFT", "JOIN", "RIGHT", "JOIN", "FULL", "JOIN"};
        String[] words1 = {"=", "(", ")"};
        try {
            String[] tokens = sql.split("\\s++");
            for (String token : tokens) {
                if (Arrays.stream(words).anyMatch(word -> token.trim().equalsIgnoreCase(word))) {
                    return null;
                }
                ;
                if (Arrays.stream(words1).anyMatch(token::contains)) {
                    return null;
                }
                ;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sql;
    }

}
