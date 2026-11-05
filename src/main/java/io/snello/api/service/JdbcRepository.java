package io.snello.api.service;

import io.snello.model.Condition;
import io.snello.model.FieldDefinition;
import io.snello.model.Metadata;
import jakarta.ws.rs.core.MultivaluedMap;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public interface JdbcRepository {


    void onLoad();

    String[] creationQueries();

    Connection getConnection() throws SQLException;

    default void openTransaction(Connection connection) throws Exception {
        connection.setAutoCommit(false);
    }

    default void closeTransaction(Connection connection) throws Exception {
        connection.commit();
    }

    default void rollbackTransaction(Connection connection) throws Exception {
        connection.rollback();
    }


    default boolean executeQuery(Connection connection, String sql) throws Exception {
        Statement statement = connection.createStatement();
        int result = statement.executeUpdate(sql);
        if (result > 0) {
            return true;
        }
        return false;
    }

    long count(String table, String alias_condition, MultivaluedMap<String, String> httpParameters, List<Condition> conditions) throws Exception;

    long count(String select_query, MultivaluedMap<String, String> httpParameters, List<Condition> conditions) throws Exception;

    long count(String select_query) throws Exception;

    boolean exist(String table, String table_key, Object uuid) throws Exception;


    List<Map<String, Object>> list(String table, String sort) throws Exception;

    List<Map<String, Object>> list(String query) throws Exception;

    List<Map<String, Object>> list(String query, MultivaluedMap<String, String> httpParameters, List<Condition> conditions, String sort, int limit, int start) throws Exception;

    List<Map<String, Object>> list(String table, String select_fields, String alias_condition, MultivaluedMap<String, String> httpParameters, List<Condition> conditions, String sort, int limit, int start) throws Exception;

    Map<String, Object> create(String table, String table_key, Map<String, Object> map) throws Exception;

    Map<String, Object> update(String table, String table_key, Map<String, Object> map, String key) throws Exception;

    Map<String, Object> fetch(String select_fields, String table, String table_key, String uuid) throws Exception;

    boolean delete(String table, String table_key, String uuid) throws Exception;

    void batch(String[] queries) throws Exception;

    boolean executeQuery(String sql) throws Exception;

    boolean verifyTable(String tableName) throws Exception;

    boolean query(String query, List<Object> values) throws Exception;

    String getJoinTableQuery();

    String escape(String name);

    String fieldDefinition2Sql(FieldDefinition fieldDefinition) throws Exception;

    String createTableSql(Metadata metadata, List<FieldDefinition> fields, List<String> joiQueries, List<Condition> conditions) throws Exception;

    /**
     * Names of the columns that currently exist on the given table, lower-cased.
     * Empty when the table does not exist. Uses JDBC metadata so it works on every supported database,
     * trying the name as given and in lower/upper case (PostgreSQL folds to lower, H2 to upper).
     */
    default Set<String> columns(String table) throws Exception {
        Set<String> result = new TreeSet<>();
        if (table == null || table.isBlank()) {
            return result;
        }
        try (Connection connection = getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            for (String candidate : new String[]{table, table.toLowerCase(), table.toUpperCase()}) {
                try (ResultSet resultSet = metaData.getColumns(connection.getCatalog(), connection.getSchema(), candidate, null)) {
                    while (resultSet.next()) {
                        // the table pattern treats '_' as a wildcard, so keep exact matches only
                        if (table.equalsIgnoreCase(resultSet.getString("TABLE_NAME"))) {
                            result.add(resultSet.getString("COLUMN_NAME").toLowerCase());
                        }
                    }
                }
                if (!result.isEmpty()) {
                    break;
                }
            }
        }
        return result;
    }

    /** ALTER statement adding one column; columnSql is a full column definition ("name type default ..."). */
    default String addColumnSql(String table, String columnSql) {
        return "ALTER TABLE " + escape(table) + " ADD COLUMN " + columnSql;
    }

    /** ALTER statement renaming a column. Supported as-is by PostgreSQL, MySQL 8+ and H2. */
    default String renameColumnSql(String table, String from, String to) {
        return "ALTER TABLE " + escape(table) + " RENAME COLUMN " + escape(from) + " TO " + escape(to);
    }

    /** ALTER statement renaming a table. Supported as-is by PostgreSQL, MySQL and H2. */
    default String renameTableSql(String from, String to) {
        return "ALTER TABLE " + escape(from) + " RENAME TO " + escape(to);
    }

}
