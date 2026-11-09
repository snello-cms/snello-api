package io.snello.service.repository;

import io.quarkus.logging.Log;
import io.snello.util.SqlHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import static io.snello.management.DbConstants.DELIMITER;
import static io.snello.management.DbConstants.PARAM;
import static io.snello.management.DbConstants.PARAM_COND_;
import static io.snello.management.DbConstants.PARAM_COND_SEPARED_;
import static io.snello.management.DbConstants.PREFIX_;
import static io.snello.management.DbConstants.SPACE;
import static io.snello.management.DbConstants.UPDATE_;
import static io.snello.management.DbConstants._CLOSE_;
import static io.snello.management.DbConstants._OPEN_;
import static io.snello.management.DbConstants._SET_;
import static io.snello.management.DbConstants._VALUES_;
import static io.snello.management.DbConstants._WHERE_;

/**
 * Shared SQL DML builder + ResultSet helpers for all JDBC dialects.
 * Dialects differ only by identifier quote ({@code `} for MySQL, empty for PG/H2 today).
 */
public final class SqlQueryBuilder {

    public static final SqlQueryBuilder PLAIN = new SqlQueryBuilder("");
    public static final SqlQueryBuilder MYSQL = new SqlQueryBuilder("`");

    private static final String INSERT_INTO = "INSERT INTO ";
    private static final String SELECT_FROM = " SELECT * FROM ";

    private final String quote;

    public SqlQueryBuilder(String quote) {
        this.quote = quote == null ? "" : quote;
    }

    public String escape(String name) {
        if (name == null) {
            return null;
        }
        return quote + name + quote;
    }

    public List<Map<String, Object>> list(ResultSet rs) throws Exception {
        ResultSetMetaData rsmd = rs.getMetaData();
        int columnCount = rsmd.getColumnCount();
        List<Map<String, Object>> lista = new ArrayList<>();
        while (rs.next()) {
            lista.add(row(rs, rsmd, columnCount));
        }
        return lista;
    }

    public Map<String, Object> single(ResultSet rs) throws Exception {
        if (rs.next()) {
            ResultSetMetaData rsmd = rs.getMetaData();
            return row(rs, rsmd, rsmd.getColumnCount());
        }
        return null;
    }

    private static Map<String, Object> row(ResultSet rs, ResultSetMetaData rsmd, int columnCount) throws Exception {
        Map<String, Object> map = new HashMap<>();
        for (int i = 1; i <= columnCount; i++) {
            // Convert CLOB/BLOB/temporal JDBC types to JSON-friendly values (String/byte[]/...).
            map.put(rsmd.getColumnLabel(i), SqlHelper.convertSqlValue(rs.getObject(i)));
        }
        return map;
    }

    public String create(String table, Map<String, Object> params) {
        if (params == null) {
            throw new IllegalArgumentException("params cannot be null");
        }
        StringJoiner columns = new StringJoiner(DELIMITER, INSERT_INTO + escape(table) + _OPEN_, _CLOSE_);
        StringJoiner values = new StringJoiner(DELIMITER, _VALUES_ + _OPEN_, _CLOSE_);
        params.forEach((key, value) -> {
            columns.add(escape(key));
            values.add(PARAM);
        });
        return columns + values.toString();
    }

    public String update(String table, Map<String, Object> params, Map<String, Object> keys, List<Object> in) {
        if (params == null) {
            throw new IllegalArgumentException("params cannot be null");
        }
        if (keys == null) {
            throw new IllegalArgumentException("keys cannot be null");
        }
        StringJoiner toSet = new StringJoiner(PARAM_COND_SEPARED_, UPDATE_ + escape(table) + _SET_, PARAM_COND_);
        StringJoiner where = new StringJoiner(DELIMITER, _WHERE_, SPACE);
        params.forEach((key, value) -> {
            in.add(value);
            toSet.add(escape(key));
        });
        keys.forEach((key, value) -> {
            where.add(key + PARAM_COND_);
            in.add(value);
        });
        return toSet.toString() + where;
    }

    public String find(String table, Map<String, Object> keys, Map<String, Object> in) {
        if (keys == null) {
            throw new IllegalArgumentException("keys cannot be null");
        }
        if (in == null) {
            throw new IllegalArgumentException("in cannot be null");
        }
        StringJoiner where = new StringJoiner(DELIMITER, _WHERE_, SPACE);
        keys.forEach((key, value) -> {
            where.add(key + PARAM_COND_);
            in.put(PREFIX_ + key, value);
        });
        return SELECT_FROM + escape(table) + where;
    }

    public void executeQueryCreate(Connection connection, String query, Map<String, Object> map, String table_key)
            throws Exception {
        try (PreparedStatement preparedStatement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            SqlHelper.fillStatement(preparedStatement, map);
            preparedStatement.executeUpdate();
            try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                if (resultSet != null) {
                    while (resultSet.next()) {
                        Object key = resultSet.getObject(1);
                        if (key != null) {
                            map.put(table_key, SqlHelper.convertSqlValue(key));
                        }
                    }
                }
            } catch (SQLException e) {
                Log.info(e.getMessage());
            }
        }
    }

    public boolean executeQuery(Connection connection, String query, List<Object> values) throws Exception {
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            SqlHelper.fillStatement(preparedStatement, values);
            int updated = preparedStatement.executeUpdate();
            return updated >= 0;
        }
    }

    public void executeQueryUpdate(Connection connection, String query, List<Object> in) throws Exception {
        try (PreparedStatement preparedStatement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            SqlHelper.fillStatement(preparedStatement, in);
            preparedStatement.executeUpdate();
            try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                if (resultSet != null) {
                    while (resultSet.next()) {
                        resultSet.getObject(1);
                    }
                }
            } catch (SQLException e) {
                Log.info(e.getMessage());
            }
        }
    }

    public List<Map<String, Object>> executeQueryList(Connection connection, String query, List<Object> in)
            throws Exception {
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            SqlHelper.fillStatement(preparedStatement, in);
            // errors propagate: they used to be logged at INFO and answered as an empty list (HTTP 200), so a broken
            // query, condition or filter looked exactly like "no data"
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return list(resultSet);
            } catch (SQLException e) {
                Log.warnf("list query failed: %s | sql=%s", e.getMessage(), query);
                throw e;
            }
        }
    }
}
