package io.snello.service.repository.mysql;

import io.snello.service.repository.SqlQueryBuilder;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

/** MySQL dialect facade over {@link SqlQueryBuilder#MYSQL} (backtick identifiers). */
public class MysqlSqlUtils {

    private static final SqlQueryBuilder SQL = SqlQueryBuilder.MYSQL;

    public static List<Map<String, Object>> list(ResultSet rs) throws Exception {
        return SQL.list(rs);
    }

    public static Map<String, Object> single(ResultSet rs) throws Exception {
        return SQL.single(rs);
    }

    public static String create(String table, Map<String, Object> params) {
        return SQL.create(table, params);
    }

    public static String update(String table, Map<String, Object> params, Map<String, Object> keys, List<Object> in) {
        return SQL.update(table, params, keys, in);
    }

    public static String find(String table, Map<String, Object> keys, Map<String, Object> in) {
        return SQL.find(table, keys, in);
    }

    public static void executeQueryCreate(Connection connection, String query, Map<String, Object> map, String table_key)
            throws Exception {
        SQL.executeQueryCreate(connection, query, map, table_key);
    }

    public static boolean executeQuery(Connection connection, String query, List<Object> values) throws Exception {
        return SQL.executeQuery(connection, query, values);
    }

    public static void executeQueryUpdate(Connection connection, String query, List<Object> in) throws Exception {
        SQL.executeQueryUpdate(connection, query, in);
    }

    public static List<Map<String, Object>> executeQueryList(Connection connection, String query, List<Object> in)
            throws Exception {
        return SQL.executeQueryList(connection, query, in);
    }

    public static String escape(String name) {
        return SQL.escape(name);
    }
}
