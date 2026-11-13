package io.snello.api.service;

import io.quarkus.logging.Log;
import io.snello.model.Condition;
import io.snello.model.FieldDefinition;
import io.snello.model.Metadata;
import io.snello.model.facet.EntityIdentity;
import io.snello.model.facet.EntityQueryFacet;
import io.snello.model.pojo.ColumnSpec;
import io.snello.model.pojo.DbColumn;
import io.snello.model.pojo.DbForeignKey;
import io.snello.util.ColumnSpecs;
import jakarta.ws.rs.core.MultivaluedMap;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    /**
     * Dialect SQL for the generated key column; null when the key is user-defined and declared among the fields.
     *
     * @param varcharLength target VARCHAR size for {@code uuid}/{@code slug} keys (see
     *                      {@link ColumnSpecs#resolveKeyVarcharLength})
     */
    String keyColumnSql(EntityQueryFacet query, int varcharLength);

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
        String table = identity.table_name();
        Log.infof("createTableSql table=%s key=%s key_type=%s columns=%d", table, query.table_key(),
                query.table_key_type(), columns == null ? 0 : columns.size());
        List<String> parts = new ArrayList<>();
        int keyVarchar = ColumnSpecs.resolveKeyVarcharLength(query, columns);
        String keyColumn = keyColumnSql(query, keyVarchar);
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
                    conditions.add(ColumnSpecs.multiJoinCondition(table, identity.uuid(), column));
                }
            }
        }
        String sql = " CREATE TABLE " + escape(table) + " (" + String.join(",", parts)
                + ", PRIMARY KEY (" + escape(query.table_key()) + ")" + createTableTail();
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
        for (DbColumn column : columnDetails(table)) {
            result.add(column.name());
        }
        return result;
    }

    /**
     * User tables in the connection catalog/schema, lower-cased, excluding views.
     * Names are the logical identifiers Snello stores on {@code metadatas.table_name}.
     */
    default List<String> tables() throws Exception {
        Set<String> names = new TreeSet<>();
        try (Connection connection = getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getTables(connection.getCatalog(), connection.getSchema(), "%",
                    new String[]{"TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    if (name != null && !name.isBlank()) {
                        names.add(name.toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        return new ArrayList<>(names);
    }

    /** Full column metadata for {@code table}, lower-cased names, ordered by ordinal. */
    default List<DbColumn> columnDetails(String table) throws Exception {
        List<DbColumn> result = new ArrayList<>();
        if (table == null || table.isBlank()) {
            return result;
        }
        try (Connection connection = getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            for (String candidate : new String[]{table, table.toLowerCase(Locale.ROOT), table.toUpperCase(Locale.ROOT)}) {
                try (ResultSet rs = metaData.getColumns(connection.getCatalog(), connection.getSchema(), candidate, null)) {
                    while (rs.next()) {
                        if (!table.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                            continue;
                        }
                        String name = rs.getString("COLUMN_NAME");
                        String typeName = rs.getString("TYPE_NAME");
                        int size = rs.getInt("COLUMN_SIZE");
                        int digits = rs.getInt("DECIMAL_DIGITS");
                        boolean nullable = rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls;
                        String def = rs.getString("COLUMN_DEF");
                        boolean autoInc = false;
                        try {
                            String isAuto = rs.getString("IS_AUTOINCREMENT");
                            autoInc = "YES".equalsIgnoreCase(isAuto);
                        } catch (SQLException ignored) {
                            // some drivers omit the column
                        }
                        result.add(new DbColumn(
                                name == null ? null : name.toLowerCase(Locale.ROOT),
                                typeName,
                                size,
                                digits,
                                nullable,
                                autoInc,
                                def));
                    }
                }
                if (!result.isEmpty()) {
                    break;
                }
            }
        }
        return result;
    }

    /** Primary-key column names (lower-cased), ordered by KEY_SEQ. */
    default List<String> primaryKeys(String table) throws Exception {
        List<String> result = new ArrayList<>();
        if (table == null || table.isBlank()) {
            return result;
        }
        try (Connection connection = getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            for (String candidate : new String[]{table, table.toLowerCase(Locale.ROOT), table.toUpperCase(Locale.ROOT)}) {
                try (ResultSet rs = metaData.getPrimaryKeys(connection.getCatalog(), connection.getSchema(), candidate)) {
                    List<String[]> rows = new ArrayList<>();
                    while (rs.next()) {
                        if (!table.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                            continue;
                        }
                        String col = rs.getString("COLUMN_NAME");
                        short seq = rs.getShort("KEY_SEQ");
                        if (col != null) {
                            rows.add(new String[]{String.valueOf(seq), col.toLowerCase(Locale.ROOT)});
                        }
                    }
                    rows.sort((a, b) -> Integer.compare(Integer.parseInt(a[0]), Integer.parseInt(b[0])));
                    for (String[] row : rows) {
                        result.add(row[1]);
                    }
                }
                if (!result.isEmpty()) {
                    break;
                }
            }
        }
        return result;
    }

    /** Outbound foreign keys of {@code table} (lower-cased names). */
    default List<DbForeignKey> importedKeys(String table) throws Exception {
        List<DbForeignKey> result = new ArrayList<>();
        if (table == null || table.isBlank()) {
            return result;
        }
        try (Connection connection = getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            for (String candidate : new String[]{table, table.toLowerCase(Locale.ROOT), table.toUpperCase(Locale.ROOT)}) {
                try (ResultSet rs = metaData.getImportedKeys(connection.getCatalog(), connection.getSchema(), candidate)) {
                    while (rs.next()) {
                        if (!table.equalsIgnoreCase(rs.getString("FKTABLE_NAME"))) {
                            continue;
                        }
                        String fkCol = rs.getString("FKCOLUMN_NAME");
                        String pkTable = rs.getString("PKTABLE_NAME");
                        String pkCol = rs.getString("PKCOLUMN_NAME");
                        result.add(new DbForeignKey(
                                fkCol == null ? null : fkCol.toLowerCase(Locale.ROOT),
                                pkTable == null ? null : pkTable.toLowerCase(Locale.ROOT),
                                pkCol == null ? null : pkCol.toLowerCase(Locale.ROOT)));
                    }
                }
                if (!result.isEmpty()) {
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Up to {@code limit} rows from {@code table} for heuristic sampling. Empty when limit ≤ 0 or the table
     * cannot be read. Failures are logged and yield an empty list (introspection must stay best-effort).
     */
    default List<Map<String, Object>> sampleRows(String table, int limit) throws Exception {
        if (table == null || table.isBlank() || limit <= 0) {
            return List.of();
        }
        String sql = "SELECT * FROM " + escape(table);
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setMaxRows(limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                int cols = rs.getMetaData().getColumnCount();
                while (rs.next() && rows.size() < limit) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= cols; i++) {
                        String label = rs.getMetaData().getColumnLabel(i);
                        if (label != null) {
                            row.put(label.toLowerCase(Locale.ROOT), rs.getObject(i));
                        }
                    }
                    rows.add(row);
                }
                return rows;
            }
        } catch (Exception e) {
            Log.warnf(e, "sampleRows failed for table=%s", table);
            return List.of();
        }
    }

    /**
     * Distinct non-null values of {@code column} with their row counts, most frequent first. Returns null when the
     * column has more than {@code maxDistinct} distinct values or cannot be read: callers use it to tell an
     * enumeration from free text, so "too many" and "unknown" both mean "not an enumeration".
     */
    default Map<String, Long> valueCounts(String table, String column, int maxDistinct) {
        String col = escape(column);
        String sql = "SELECT " + col + ", COUNT(*) FROM " + escape(table) + " WHERE " + col + " IS NOT NULL GROUP BY "
                + col + " ORDER BY COUNT(*) DESC";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setMaxRows(maxDistinct + 1);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Long> counts = new LinkedHashMap<>();
                while (rs.next()) {
                    Object value = rs.getObject(1);
                    if (value == null || String.valueOf(value).isBlank()) {
                        continue;
                    }
                    counts.put(String.valueOf(value), rs.getLong(2));
                    if (counts.size() > maxDistinct) {
                        return null;
                    }
                }
                return counts;
            }
        } catch (Exception e) {
            Log.debugf("valueCounts failed table=%s column=%s: %s", table, column, e.getMessage());
            return null;
        }
    }

    /**
     * How many of {@code values} exist in {@code table.column}; -1 when the lookup cannot run (missing table,
     * incompatible types). Used to confirm a guessed reference against real data.
     */
    default int countMatching(String table, String column, Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        String col = escape(column);
        String placeholders = String.join(",", java.util.Collections.nCopies(values.size(), "?"));
        String sql = "SELECT COUNT(DISTINCT " + col + ") FROM " + escape(table) + " WHERE " + col + " IN ("
                + placeholders + ")";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            int i = 1;
            for (String value : values) {
                io.snello.util.SqlHelper.bindKey(ps, i++, value);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            Log.debugf("countMatching failed table=%s column=%s: %s", table, column, e.getMessage());
            return -1;
        }
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
