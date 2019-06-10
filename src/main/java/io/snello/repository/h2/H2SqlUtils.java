package io.snello.repository.h2;

import io.snello.util.SqlHelper;

import java.sql.*;
import java.util.*;

import static io.snello.management.DbConstants.*;
import static io.snello.repository.h2.H2Constants.*;

public class H2SqlUtils {


    public static List<Map<String, Object>> list(final ResultSet rs)
            throws Exception {
        ResultSetMetaData rsmd = rs.getMetaData();
        int columnCount = rsmd.getColumnCount();
        List<Map<String, Object>> lista = new ArrayList<>();
        while (rs.next()) {
            lista.add(row(rs, rsmd, columnCount));
        }
        return lista;
    }


    public static Map<String, Object> single(ResultSet rs) throws Exception {
        if (rs.next()) {
            ResultSetMetaData rsmd = rs.getMetaData();
            int columnCount = rsmd.getColumnCount();
            return row(rs, rsmd, columnCount);
        }
        return null;
    }

    private static Map<String, Object> row(ResultSet rs, ResultSetMetaData rsmd, int columnCount) throws Exception {
        final Map<String, Object> map = new HashMap<>();
        for (int i = 1; i <= columnCount; i++) {
            map.put(rsmd.getColumnLabel(i), rs.getObject(i));
        }
        return map;
    }

    public static String create(String table, Map<String, Object> params) {
        StringJoiner columns = new StringJoiner(DELIMITER, INSERT_INTO + escape(table) + _OPEN_, _CLOSE_);
        StringJoiner values = new StringJoiner(DELIMITER, _VALUES_ + _OPEN_, _CLOSE_);
        params.forEach(
                (key, value) -> {
                    columns.add(ESCAPE + key + ESCAPE);
                    values.add(PARAM);
                }
        );
        return columns.toString() + values.toString();
    }

    public static String update(String table, Map<String, Object> params, Map<String, Object> keys, List<Object> in) {
        StringJoiner toSet = new StringJoiner(PARAM_COND_SEPARED_, UPDATE_ + escape(table) + _SET_, PARAM_COND_);
        StringJoiner where = new StringJoiner(DELIMITER, _WHERE_, SPACE);
        params.forEach(
                (key, value) -> {
                    in.add(value);
                    toSet.add(ESCAPE + key + ESCAPE);
                }
        );
        keys.forEach(
                (key, value) -> {
                    where.add(key + PARAM_COND_);
                    in.add(value);
                }
        );
        return toSet.toString() + where.toString();
    }


    public static String find(String table, Map<String, Object> keys, Map<String, Object> in) {
        StringJoiner where = new StringJoiner(DELIMITER, _WHERE_, SPACE);
        keys.forEach(
                (key, value) -> {
                    where.add(key + PARAM_COND_);
                    in.put(PREFIX_ + key, value);
                }
        );
        return SELECT_FROM + escape(table) + where.toString();
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

    public static boolean executeQuery(Connection connection, String query, List<Object> values) throws Exception {
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            SqlHelper.fillStatement(preparedStatement, values);
            int updated = preparedStatement.executeUpdate();
            return updated >= 0;
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
                return H2SqlUtils.list(resultSet);
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        }
        return null;
    }


    public static String escape(String name) {
        return ESCAPE + name + ESCAPE;
    }
}
