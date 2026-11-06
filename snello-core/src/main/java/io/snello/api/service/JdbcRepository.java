package io.snello.api.service;

import io.quarkus.logging.Log;
import io.snello.model.Condition;
import io.snello.model.FieldDefinition;
import io.snello.model.Metadata;
import io.snello.model.facet.EntityIdentity;
import io.snello.model.facet.EntityQueryFacet;
import io.snello.model.pojo.ColumnSpec;
import io.snello.util.ColumnSpecs;
import jakarta.ws.rs.core.MultivaluedMap;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public interface JdbcRepository {


    void onLoad();

    String[] creationQueries();

    /**
     * Idempotent schema upgrades run one by one after {@link #creationQueries()} (each in its own statement, so
     * one already-applied ALTER cannot abort the others the way it would inside a JDBC batch).
     */
    default String[] upgradeQueries() {
        return new String[0];
    }

    /** True when {@code e} only says the upgrade was already applied (e.g. MySQL "duplicate column"). */
    default boolean isAlreadyApplied(Exception e) {
        return false;
    }

    /** Runs {@link #upgradeQueries()}; failures are logged, never fatal. */
    default void applyUpgrades() {
        for (String sql : upgradeQueries()) {
            try {
                executeQuery(sql);
                io.quarkus.logging.Log.infof("schema upgrade applied: %s", sql);
            } catch (Exception e) {
                if (isAlreadyApplied(e)) {
                    io.quarkus.logging.Log.debugf("schema upgrade already applied: %s", sql);
                } else {
                    io.quarkus.logging.Log.errorf(e, "schema upgrade failed: %s", sql);
                }
            }
        }
    }

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

    /** Dialect SQL for one column (without the raw {@code sql_definition} override); null if the kind is null. */
    String columnSql(ColumnSpec column);

    /** Dialect SQL for the generated key column; null when the key is user-defined and declared among the fields. */
    String keyColumnSql(EntityQueryFacet query);

    /** Closes a CREATE TABLE after the primary key (e.g. {@code ") ENGINE=INNODB;"} on MySQL). */
    default String createTableTail() {
        return ") ;";
    }

    default String fieldDefinition2Sql(FieldDefinition fieldDefinition) throws Exception {
        return fieldDefinition == null ? null : columnSql(ColumnSpecs.of(fieldDefinition));
    }

    /** Flat-DTO entry point, kept for existing callers: splits the rows into facets and delegates. */
    default String createTableSql(Metadata metadata, List<FieldDefinition> fields, List<String> joiQueries,
                                  List<Condition> conditions) throws Exception {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata cannot be null");
        }
        List<ColumnSpec> columns = new ArrayList<>();
        for (FieldDefinition fd : fields == null ? List.<FieldDefinition>of() : fields) {
            if (fd != null) {
                columns.add(ColumnSpecs.of(fd));
            }
        }
        return createTableSql(metadata.identity(), metadata.queryFacet(), columns, joiQueries, conditions);
    }

    /**
     * CREATE TABLE from storage facets only: identity + entity query facet + resolved columns. Multi-join columns add
     * their join-table DDL to {@code joinQueries} and their filter row to {@code conditions}.
     */
    default String createTableSql(EntityIdentity identity, EntityQueryFacet query, List<ColumnSpec> columns,
                                  List<String> joinQueries, List<Condition> conditions) {
        String table = identity.table_name;
        Log.infof("createTableSql table=%s key=%s key_type=%s columns=%d", table, query.table_key,
                query.table_key_type, columns == null ? 0 : columns.size());
        List<String> parts = new ArrayList<>();
        String keyColumn = keyColumnSql(query);
        if (keyColumn != null) {
            parts.add(keyColumn);
        }
        for (ColumnSpec column : columns == null ? List.<ColumnSpec>of() : columns) {
            if (column.hasSqlDefinition()) {
                parts.add(column.sqlDefinition());
            } else {
                String columnSql = columnSql(column);
                if (columnSql == null || columnSql.trim().isEmpty()) {
                    throw new IllegalArgumentException("Unsupported field type in createTableSql: name='"
                            + column.name() + "', " + column.sourceType());
                }
                parts.add(columnSql);
            }
            if (column.multiJoin()) {
                String joinTable = ColumnSpecs.joinTableName(table, column);
                Log.infof("createTableSql multijoin column %s -> join table %s", column.name(), joinTable);
                if (joinQueries != null) {
                    joinQueries.add(String.format(getJoinTableQuery(), joinTable, table + "_id",
                            column.joinTableName() + "_id"));
                }
                if (conditions != null) {
                    conditions.add(ColumnSpecs.multiJoinCondition(table, identity.uuid, column));
                }
            }
        }
        String sql = " CREATE TABLE " + escape(table) + " (" + String.join(",", parts)
                + ", PRIMARY KEY (" + escape(query.table_key) + ")" + createTableTail();
        Log.info("QUERY CREATION TABLE: " + sql);
        return sql;
    }

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
