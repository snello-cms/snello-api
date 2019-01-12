package io.snello.cms.util;

import java.sql.*;
import java.util.*;

public class SqlUtils {


    public static List<Map<String, Object>> list(final ResultSet rs)
            throws Exception {
        final var rsmd = rs.getMetaData();
        final var columnCount = rsmd.getColumnCount();
        List<Map<String, Object>> lista = new ArrayList<>();
        while (rs.next()) {
            lista.add(row(rs, rsmd, columnCount));
        }
        return lista;
    }


    public static Map<String, Object> single(ResultSet rs) throws Exception {
        if (rs.next()) {
            final var rsmd = rs.getMetaData();
            final var columnCount = rsmd.getColumnCount();
            return row(rs, rsmd, columnCount);
        }
        return null;
    }

    private static Map<String, Object> row(ResultSet rs, ResultSetMetaData rsmd, int columnCount) throws Exception {
        final Map<String, Object> map = new HashMap<>();
        for (var i = 1; i <= columnCount; i++) {
            map.put(rsmd.getColumnLabel(i), rs.getObject(i));
        }
        return map;
    }

    public static String create(String table, Map<String, Object> params) {
        StringJoiner columns = new StringJoiner(",", "INSERT INTO " + table + " ( ", " ) ");
        StringJoiner values = new StringJoiner(",", " VALUES ( ", " )");
        params.forEach(
                (key, value) -> {
                    columns.add("`" + key + "`");
                    values.add("?");
                }
        );
        return columns.toString() + values.toString();
    }

    public static String update(String table, Map<String, Object> params, Map<String, Object> keys, List<Object> in) {
        StringJoiner toSet = new StringJoiner("=?, ", "UPDATE " + table + " SET ", "=? ");
        StringJoiner where = new StringJoiner(",", " WHERE ", " ");
        params.forEach(
                (key, value) -> {
                    in.add(value);
                    toSet.add("`" + key + "`");
                }
        );
        keys.forEach(
                (key, value) -> {
                    where.add(key + "=?");
                    in.add(value);
                }
        );
        return toSet.toString() + where.toString();
    }


    public static String find(String table, Map<String, Object> keys, Map<String, Object> in) {
        StringJoiner where = new StringJoiner(",", " WHERE ", " ");
        keys.forEach(
                (key, value) -> {
                    where.add(key + "=?");
                    in.put("k_" + key, value);
                }
        );
        return "select * FROM " + table + where.toString();
    }

    public static void executeQueryCreate(Connection connection, String query, Map<String, Object> map, String table_key) throws Exception {
        try (PreparedStatement preparedStatement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            SqlHelper.fillStatement(preparedStatement, map);
            int updated = preparedStatement.executeUpdate();
            try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                // the resource might also fail
                // specially on oracle DBMS
                if (resultSet != null) {
                    while (resultSet.next()) {
                        Object key = resultSet.getObject(1);
                        if (key != null) {
                            map.put(table_key, SqlHelper.convertSqlValue(key));
                        }
                    }
                }
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    public static void executeQueryUpdate(Connection connection, String query, List<Object> in) throws Exception {
        try (PreparedStatement preparedStatement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            SqlHelper.fillStatement(preparedStatement, in);
            int updated = preparedStatement.executeUpdate();
            try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                // the resource might also fail
                // specially on oracle DBMS
                if (resultSet != null) {
                    while (resultSet.next()) {
                        Object key = resultSet.getObject(1);
                    }
                }
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    public static List<Map<String, Object>> executeQueryList(Connection connection, String query, List<Object> in) throws Exception {
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            SqlHelper.fillStatement(preparedStatement, in);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return SqlUtils.list(resultSet);
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        }
        return null;
    }
}
