package io.snello.util;

import io.quarkus.logging.Log;

import java.util.regex.Pattern;

/**
 * Decides how request filters are appended to a stored SelectQuery SQL.
 * <p>
 * The old check was {@code sql.contains(" WHERE ")}, which a WHERE inside a subquery (or a string literal) satisfied,
 * so the filters were appended without any keyword and produced invalid SQL. Only a <em>top-level</em> WHERE counts.
 */
public final class SqlWhere {

    private static final Pattern WHERE = Pattern.compile("\\bWHERE\\b");
    private static final Pattern DANGLING = Pattern.compile("\\b(WHERE|AND|OR)\\s*$");
    private static final Pattern NOT_APPENDABLE = Pattern.compile(
            "\\b(GROUP\\s+BY|HAVING|ORDER\\s+BY|LIMIT|OFFSET|FETCH|UNION|INTERSECT|EXCEPT|WINDOW)\\b");

    /**
     * True when filters / sort / paging cannot simply be appended to {@code sql}: it already has a top-level
     * GROUP BY, HAVING, ORDER BY, LIMIT, set operation, ... ("... GROUP BY hull WHERE hull = ?" is a syntax error).
     * Such a query must be wrapped as a subquery first.
     */
    public static boolean needsWrap(String sql) {
        boolean wrap = NOT_APPENDABLE.matcher(topLevelOnly(sql).toUpperCase()).find();
        Log.debugf("sql needsWrap=%s for query=%s", wrap, sql);
        return wrap;
    }

    private SqlWhere() {
    }

    /**
     * Text to put between {@code sql} and a non-empty filter expression: {@code " WHERE "} when the query has no
     * top-level WHERE, {@code " AND "} when it has one, and a plain space when the query already ends with
     * WHERE / AND / OR (a hand-written "... WHERE" template).
     */
    public static String joiner(String sql) {
        String topLevel = topLevelOnly(sql).toUpperCase();
        String joiner;
        if (!WHERE.matcher(topLevel).find()) {
            joiner = " WHERE ";
        } else if (DANGLING.matcher(topLevel.trim()).find()) {
            joiner = " ";
        } else {
            joiner = " AND ";
        }
        Log.debugf("sql where joiner='%s' for query=%s", joiner, sql);
        return joiner;
    }

    /** {@code sql} with string literals and everything inside parentheses blanked out. */
    static String topLevelOnly(String sql) {
        if (sql == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(sql.length());
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (inString) {
                if (c == '\'') {
                    inString = false;
                }
                continue;
            }
            if (c == '\'') {
                inString = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                if (depth > 0) {
                    depth--;
                }
            } else if (depth == 0) {
                out.append(c);
            }
        }
        return out.toString();
    }
}
