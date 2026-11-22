package io.snello.service.repository.mssql;

import io.quarkus.logging.Log;
import io.snello.api.service.JdbcRepository;
import io.snello.model.Condition;
import io.snello.model.enums.KeyType;
import io.snello.model.facet.EntityQueryFacet;
import io.snello.model.pojo.ColumnSpec;
import io.snello.model.pojo.DbForeignKey;
import io.snello.util.ConditionUtils;
import io.snello.util.SqlHelper;
import jakarta.ws.rs.core.MultivaluedMap;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static io.snello.management.DbConstants.*;
import static io.snello.service.repository.mssql.MssqlConstants.*;

public class MssqlJdbcRepository implements JdbcRepository {

    private final DataSource dataSource;

    public MssqlJdbcRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
    }

    public void onLoad() {
        Log.info("Creation queries at startup: ");
        try {
            batch(creationQueries());
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }

    @Override
    public String[] upgradeQueries() {
        // SQL Server has no ADD IF NOT EXISTS: guard with COL_LENGTH so restarts stay quiet
        // (selectqueries already ships api_protected/username_field in CREATE; bare ALTER logged ERROR every boot).
        return new String[]{
                """
                        IF COL_LENGTH('metadatas', 'ai_profile') IS NULL \
                        ALTER TABLE metadatas ADD ai_profile nvarchar(max)""",
                """
                        IF COL_LENGTH('fielddefinitions', 'ai_profile') IS NULL \
                        ALTER TABLE fielddefinitions ADD ai_profile nvarchar(max)""",
                """
                        IF COL_LENGTH('metadatas', 'api_protected') IS NULL \
                        ALTER TABLE metadatas ADD api_protected bit NOT NULL \
                        CONSTRAINT DF_metadatas_api_protected DEFAULT 0""",
                """
                        IF COL_LENGTH('metadatas', 'username_field') IS NULL \
                        ALTER TABLE metadatas ADD username_field nvarchar(255) NULL""",
                """
                        IF COL_LENGTH('selectqueries', 'api_protected') IS NULL \
                        ALTER TABLE selectqueries ADD api_protected bit NOT NULL \
                        CONSTRAINT DF_selectqueries_api_protected DEFAULT 0""",
                """
                        IF COL_LENGTH('selectqueries', 'username_field') IS NULL \
                        ALTER TABLE selectqueries ADD username_field nvarchar(255) NULL""",
                // action scripts outgrew 1000 chars; no-op when already nvarchar(max)
                """
                        IF OBJECT_ID(N'actions', N'U') IS NOT NULL \
                        AND COL_LENGTH('actions', 'body') IS NOT NULL \
                        ALTER TABLE actions ALTER COLUMN body nvarchar(max) NULL""",
                // Introspect / import: select options and CSV field lists outgrow nvarchar(255)
                // (same widen as PostgreSQL TEXT / H2 wide varchar). COL_LENGTH = -1 means already max.
                """
                        IF OBJECT_ID(N'fielddefinitions', N'U') IS NOT NULL AND COL_LENGTH('fielddefinitions', 'options') > 0 \
                        ALTER TABLE fielddefinitions ALTER COLUMN options nvarchar(max) NULL""",
                """
                        IF OBJECT_ID(N'fielddefinitions', N'U') IS NOT NULL AND COL_LENGTH('fielddefinitions', 'label') > 0 \
                        ALTER TABLE fielddefinitions ALTER COLUMN label nvarchar(max) NOT NULL""",
                """
                        IF OBJECT_ID(N'fielddefinitions', N'U') IS NOT NULL AND COL_LENGTH('fielddefinitions', 'description') > 0 \
                        ALTER TABLE fielddefinitions ALTER COLUMN description nvarchar(max) NULL""",
                """
                        IF OBJECT_ID(N'fielddefinitions', N'U') IS NOT NULL AND COL_LENGTH('fielddefinitions', 'validations') > 0 \
                        ALTER TABLE fielddefinitions ALTER COLUMN validations nvarchar(max) NULL""",
                """
                        IF OBJECT_ID(N'fielddefinitions', N'U') IS NOT NULL AND COL_LENGTH('fielddefinitions', 'function_def') > 0 \
                        ALTER TABLE fielddefinitions ALTER COLUMN function_def nvarchar(max) NULL""",
                """
                        IF OBJECT_ID(N'fielddefinitions', N'U') IS NOT NULL AND COL_LENGTH('fielddefinitions', 'sql_type') > 0 \
                        ALTER TABLE fielddefinitions ALTER COLUMN sql_type nvarchar(max) NULL""",
                """
                        IF OBJECT_ID(N'fielddefinitions', N'U') IS NOT NULL AND COL_LENGTH('fielddefinitions', 'sql_definition') > 0 \
                        ALTER TABLE fielddefinitions ALTER COLUMN sql_definition nvarchar(max) NULL""",
                """
                        IF OBJECT_ID(N'fielddefinitions', N'U') IS NOT NULL AND COL_LENGTH('fielddefinitions', 'default_value') > 0 \
                        ALTER TABLE fielddefinitions ALTER COLUMN default_value nvarchar(max) NULL""",
                """
                        IF OBJECT_ID(N'fielddefinitions', N'U') IS NOT NULL AND COL_LENGTH('fielddefinitions', 'pattern') > 0 \
                        ALTER TABLE fielddefinitions ALTER COLUMN pattern nvarchar(max) NULL""",
                """
                        IF OBJECT_ID(N'fielddefinitions', N'U') IS NOT NULL AND COL_LENGTH('fielddefinitions', 'join_table_select_fields') > 0 \
                        ALTER TABLE fielddefinitions ALTER COLUMN join_table_select_fields nvarchar(max) NULL""",
                """
                        IF OBJECT_ID(N'fielddefinitions', N'U') IS NOT NULL AND COL_LENGTH('fielddefinitions', 'search_condition') > 0 \
                        ALTER TABLE fielddefinitions ALTER COLUMN search_condition nvarchar(max) NULL""",
                """
                        IF OBJECT_ID(N'fielddefinitions', N'U') IS NOT NULL AND COL_LENGTH('fielddefinitions', 'search_field_name') > 0 \
                        ALTER TABLE fielddefinitions ALTER COLUMN search_field_name nvarchar(max) NULL""",
                """
                        IF OBJECT_ID(N'metadatas', N'U') IS NOT NULL AND COL_LENGTH('metadatas', 'select_fields') > 0 \
                        ALTER TABLE metadatas ALTER COLUMN select_fields nvarchar(max) NULL""",
                """
                        IF OBJECT_ID(N'metadatas', N'U') IS NOT NULL AND COL_LENGTH('metadatas', 'search_fields') > 0 \
                        ALTER TABLE metadatas ALTER COLUMN search_fields nvarchar(max) NULL""",
                """
                        IF OBJECT_ID(N'metadatas', N'U') IS NOT NULL AND COL_LENGTH('metadatas', 'description') > 0 \
                        ALTER TABLE metadatas ALTER COLUMN description nvarchar(max) NULL""",
                """
                        IF OBJECT_ID(N'metadatas', N'U') IS NOT NULL AND COL_LENGTH('metadatas', 'alias_condition') > 0 \
                        ALTER TABLE metadatas ALTER COLUMN alias_condition nvarchar(max) NULL""",
                """
                        IF OBJECT_ID(N'metadatas', N'U') IS NOT NULL AND COL_LENGTH('metadatas', 'creation_query') > 0 \
                        ALTER TABLE metadatas ALTER COLUMN creation_query nvarchar(max) NULL""",
                """
                        IF OBJECT_ID(N'metadatas', N'U') IS NOT NULL AND COL_LENGTH('metadatas', 'order_by') > 0 \
                        ALTER TABLE metadatas ALTER COLUMN order_by nvarchar(max) NULL""",
                """
                        IF OBJECT_ID(N'metadatas', N'U') IS NOT NULL AND COL_LENGTH('metadatas', 'tab_groups') > 0 \
                        ALTER TABLE metadatas ALTER COLUMN tab_groups nvarchar(max) NULL""",
                creationIntrospectJobs,
                creationIntrospectJobLogs,
                creationIntrospectJobLogsIndex
        };
    }

    @Override
    public boolean isAlreadyApplied(Exception e) {
        // 2705 duplicate column / 2714 object exists / 1913 duplicate index — also walk cause + next
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (!(t instanceof SQLException sql)) {
                continue;
            }
            for (; sql != null; sql = sql.getNextException()) {
                int code = sql.getErrorCode();
                if (code == 2705 || code == 2714 || code == 1913) {
                    return true;
                }
                String msg = sql.getMessage();
                if (msg != null && (msg.contains("specified more than once")
                        || msg.contains("Column names in each table must be unique")
                        || msg.contains("already an object named"))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String[] creationQueries() {
        return new String[]{
                creationActionExtensions,
                creationQueryMetadatas,
                creationQueryFieldDefinitions,
                creationQueryConditions,
                creationQueryDocuments,
                creationQuerySelectQueries,
                creationLinksQueries,
                creationAiTools,
                creationHistorySession,
                creationHistoryEvent,
                creationAiProject,
                creationAiSkill,
                creationAiMcpTool,
                creationAiUiCard,
                creationAiServiceAccount,
                creationScheduledActions,
                creationPrintTemplates,
                creationWorkflowPages,
                creationWorkflowDefinitions,
                creationHumanTasks,
                creationLayouts,
                creationLayoutsIndex,
                creationIntrospectJobs,
                creationIntrospectJobLogs,
                creationIntrospectJobLogsIndex
        };
    }

    public long count(String table, String alias_condition, MultivaluedMap<String, String> httpParameters,
                      List<Condition> conditions) throws Exception {
        StringBuffer where = new StringBuffer();
        StringBuffer select = new StringBuffer();
        List<Object> in = new LinkedList<>();
        select.append(COUNT_QUERY);
        if (alias_condition != null) {
            where.append(alias_condition);
        }
        ConditionUtils.whereWithParams(httpParameters, conditions, where, in);
        try (Connection connection = dataSource.getConnection()) {
            if (!where.isEmpty()) {
                where = new StringBuffer(_WHERE_).append(where);
            }
            Log.info("query: " + select + MssqlSqlUtils.escape(table) + where);
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    select + MssqlSqlUtils.escape(table) + where)) {
                SqlHelper.fillStatement(preparedStatement, in);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        long count = resultSet.getLong(SIZE_OF);
                        Log.info("count:" + count);
                        return count;
                    }
                }
            }
        }
        return 0;
    }

    public long count(String select_query, MultivaluedMap<String, String> httpParameters, List<Condition> conditions)
            throws Exception {
        StringBuffer where = new StringBuffer();
        StringBuffer select = new StringBuffer(select_query);
        List<Object> in = new LinkedList<>();
        ConditionUtils.whereWithParams(httpParameters, conditions, where, in);
        try (Connection connection = dataSource.getConnection()) {
            if (!where.isEmpty()) {
                where = new StringBuffer(_WHERE_).append(where);
            }
            Log.info("query: " + select + where);
            try (PreparedStatement preparedStatement = connection.prepareStatement(select.toString() + where)) {
                SqlHelper.fillStatement(preparedStatement, in);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        long count = resultSet.getLong(SIZE_OF);
                        Log.info("count:" + count);
                        return count;
                    }
                }
            }
        }
        return 0;
    }

    public long count(String select_query) throws Exception {
        StringBuffer where = new StringBuffer();
        StringBuffer select = new StringBuffer(select_query);
        List<Object> in = new LinkedList<>();
        try (Connection connection = dataSource.getConnection()) {
            if (!where.isEmpty()) {
                where = new StringBuffer(_WHERE_).append(where);
            }
            Log.info("query: " + select + where);
            try (PreparedStatement preparedStatement = connection.prepareStatement(select.toString() + where)) {
                SqlHelper.fillStatement(preparedStatement, in);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        long count = resultSet.getLong(SIZE_OF);
                        Log.info("count:" + count);
                        return count;
                    }
                }
            }
        }
        return 0;
    }

    public boolean exist(String table, String table_key, Object uuid) throws Exception {
        String select = COUNT_QUERY + MssqlSqlUtils.escape(table) + _WHERE_ + MssqlSqlUtils.escape(table_key) + "= ?";
        List<Object> in = new LinkedList<>();
        in.add(uuid);
        try (Connection connection = dataSource.getConnection()) {
            Log.info("query: " + select);
            try (PreparedStatement preparedStatement = connection.prepareStatement(select)) {
                SqlHelper.fillStatement(preparedStatement, in);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        long count = resultSet.getLong(SIZE_OF);
                        Log.info("exist:" + (count > 0));
                        return count > 0;
                    }
                }
            }
        }
        return false;
    }

    public List<Map<String, Object>> list(String table, String sort) throws Exception {
        return list(table, null, null, null, null, sort, 0, 0);
    }

    public List<Map<String, Object>> list(String table, String select_fields, String alias_condition,
                                          MultivaluedMap<String, String> httpParameters, List<Condition> conditions,
                                          String sort, int limit, int start) throws Exception {
        StringBuffer where = new StringBuffer();
        StringBuffer order_limit = new StringBuffer();
        StringBuffer select = new StringBuffer();
        List<Object> in = new LinkedList<>();
        select.append(_SELECT_);
        if (select_fields != null) {
            select.append(select_fields);
        } else {
            select.append(_ALL_);
        }
        select.append(_FROM_);
        if (alias_condition != null && !alias_condition.trim().isEmpty()) {
            where.append(MssqlSqlUtils.escape(alias_condition));
        }

        ConditionUtils.whereWithParams(httpParameters, conditions, where, in);
        appendOrderAndPage(order_limit, in, sort, limit, start);
        try (Connection connection = dataSource.getConnection()) {
            if (!where.isEmpty()) {
                where = new StringBuffer(_WHERE_).append(where);
            }
            Log.info("LIST query: " + select + MssqlSqlUtils.escape(table) + where + order_limit);
            return MssqlSqlUtils.executeQueryList(connection,
                    select.toString() + MssqlSqlUtils.escape(table) + where + order_limit, in);
        }
    }

    public List<Map<String, Object>> list(String query, MultivaluedMap<String, String> httpParameters,
                                          List<Condition> conditions, String sort, int limit, int start)
            throws Exception {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("query cannot be null or empty");
        }
        StringBuffer where = new StringBuffer();
        StringBuffer order_limit = new StringBuffer();
        StringBuffer select = new StringBuffer(query);
        List<Object> in = new LinkedList<>();

        ConditionUtils.whereWithParams(httpParameters, conditions, where, in);
        appendOrderAndPage(order_limit, in, sort, limit, start);
        try (Connection connection = dataSource.getConnection()) {
            if (!where.isEmpty()) {
                where = new StringBuffer(io.snello.util.SqlWhere.joiner(select.toString())).append(where);
            }
            Log.info("LIST query: " + select + where + order_limit);
            return MssqlSqlUtils.executeQueryList(connection, select.toString() + where + order_limit, in);
        }
    }

    /**
     * SQL Server requires ORDER BY when using OFFSET/FETCH. Default to {@code (SELECT NULL)} when no sort
     * is provided but paging is requested; random sort uses {@code NEWID()}.
     */
    private static void appendOrderAndPage(StringBuffer orderLimit, List<Object> in, String sort, int limit,
                                           int start) {
        boolean paging = !(start == 0 && limit == 0);
        String normalizedSort = sort == null ? null : sort.trim();
        if (normalizedSort != null && !normalizedSort.isEmpty()) {
            if (normalizedSort.toLowerCase().contains("random")) {
                orderLimit.append(_ORDER_BY_).append("NEWID()");
            } else if (normalizedSort.contains(":")) {
                String[] sort_ = normalizedSort.split(":");
                orderLimit.append(_ORDER_BY_).append(sort_[0]).append(" ").append(sort_[1]);
            } else {
                orderLimit.append(_ORDER_BY_).append(normalizedSort);
            }
        } else if (paging) {
            orderLimit.append(_ORDER_BY_).append("(SELECT NULL)");
        }

        if (!paging) {
            Log.info("no limits");
            return;
        }
        int offset = Math.max(start, 0);
        int fetch = limit > 0 ? limit : 10;
        orderLimit.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY ");
        in.add(offset);
        in.add(fetch);
    }

    public List<Map<String, Object>> list(String query) throws Exception {
        List<Object> in = new LinkedList<>();
        try (Connection connection = dataSource.getConnection()) {
            Log.info("LIST query: " + query);
            return MssqlSqlUtils.executeQueryList(connection, query, in);
        }
    }

    public Map<String, Object> create(String table, String table_key, Map<String, Object> map) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String query = MssqlSqlUtils.create(table, map);
            Log.info("CREATE QUERY: " + query);
            MssqlSqlUtils.executeQueryCreate(connection, query, map, table_key);
        }
        return map;
    }

    public boolean query(String query, List<Object> values) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            Log.info("EXECUTE QUERY: " + query);
            return MssqlSqlUtils.executeQuery(connection, query, values);
        } catch (Exception e) {
            Log.error("error: ", e);
            return false;
        }
    }

    public Map<String, Object> update(String table, String table_key, Map<String, Object> map, String key)
            throws Exception {
        Map<String, Object> keys = new HashMap<>();
        List<Object> in = new LinkedList<>();
        keys.put(table_key, key);
        String query = MssqlSqlUtils.update(table, map, keys, in);
        try (Connection connection = dataSource.getConnection()) {
            Log.info("UPDATE QUERY: " + query);
            MssqlSqlUtils.executeQueryUpdate(connection, query, in);
        }
        return map;
    }

    public Map<String, Object> fetch(String select_fields, String table, String table_key, String uuid)
            throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (select_fields == null) {
                select_fields = " * ";
            }
            Log.info("FETCH QUERY: " + "_SELECT_ * _FROM_ " + MssqlSqlUtils.escape(table) + " _WHERE_ " + table_key
                    + " = ?");
            PreparedStatement preparedStatement = connection.prepareStatement(
                    _SELECT_ + select_fields + _FROM_ + MssqlSqlUtils.escape(table)
                    + _WHERE_ + MssqlSqlUtils.escape(table_key) + " = ?");
            SqlHelper.bindKey(preparedStatement, 1, uuid);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return MssqlSqlUtils.single(resultSet);
            }
        }
    }

    public boolean delete(String table, String table_key, String uuid) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            Log.info("DELETE QUERY: " + DELETE_FROM + table + _WHERE_ + table_key + " = ? ");
            PreparedStatement preparedStatement = connection.prepareStatement(
                    DELETE_FROM + MssqlSqlUtils.escape(table) + _WHERE_
                    + MssqlSqlUtils.escape(table_key) + " = ?");
            SqlHelper.bindKey(preparedStatement, 1, uuid);
            int result = preparedStatement.executeUpdate();
            return result > 0;
        }
    }

    public void batch(String[] queries) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            // Prefer per-statement execution: IF OBJECT_ID + CREATE mixes poorly in a single JDBC batch.
            for (String query : queries) {
                Log.info("BATCH QUERY: " + query);
                try (Statement statement = connection.createStatement()) {
                    statement.execute(query);
                }
            }
        }
    }

    public boolean executeQuery(String sql) throws Exception {
        Statement statement = null;
        try (Connection connection = dataSource.getConnection()) {
            statement = connection.createStatement();
            int result = statement.executeUpdate(sql);
            if (result > 0) {
                return true;
            }
        } finally {
            if (statement != null) {
                statement.close();
            }
        }
        return false;
    }

    public boolean verifyTable(String tableName) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(VERIFY_TABLE)) {
                preparedStatement.setObject(1, tableName);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        return true;
                    }
                }
            }
            // SQL Server INFORMATION_SCHEMA is often case-preserving; try alternate casing
            if (tableName != null) {
                for (String alt : new String[]{tableName.toLowerCase(), tableName.toUpperCase()}) {
                    if (alt.equals(tableName)) {
                        continue;
                    }
                    try (PreparedStatement preparedStatement = connection.prepareStatement(VERIFY_TABLE)) {
                        preparedStatement.setObject(1, alt);
                        try (ResultSet resultSet = preparedStatement.executeQuery()) {
                            if (resultSet.next()) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public String getJoinTableQuery() {
        return joinTableQuery;
    }

    @Override
    public String escape(String name) {
        return MssqlSqlUtils.escape(name);
    }

    /**
     * Microsoft JDBC often returns a null schema; unscoped {@code getTables}/{@code getImportedKeys} then scan every
     * schema and hang for minutes. Default to {@code dbo} (migration/other schemas stay out of introspect).
     */
    @Override
    public String metadataSchema(Connection connection) throws SQLException {
        String schema = connection.getSchema();
        return (schema == null || schema.isBlank()) ? "dbo" : schema;
    }

    /** SQL Server lookups are case-insensitive; one candidate avoids 3× {@code getImportedKeys} cost. */
    @Override
    public String[] metadataTableCandidates(String table) {
        if (table == null || table.isBlank()) {
            return new String[0];
        }
        return new String[]{table};
    }

    @Override
    public String selectStarSampleSql(String table, int limit) {
        return "SELECT TOP (" + Math.max(0, limit) + ") * FROM " + escape(table);
    }

    @Override
    public String selectColumnSampleSql(String table, String column, int limit) {
        String col = escape(column);
        return "SELECT TOP (" + Math.max(0, limit) + ") " + col + " FROM " + escape(table)
                + " WHERE " + col + " IS NOT NULL";
    }

    /**
     * {@link DatabaseMetaData#getImportedKeys} is notoriously slow on SQL Server (catalog scans). Resolve FKs from
     * {@code sys.foreign_keys} instead so introspect preview can finish before SPA/ingress timeouts.
     */
    @Override
    public List<DbForeignKey> importedKeys(String table) throws Exception {
        List<DbForeignKey> result = new ArrayList<>();
        if (table == null || table.isBlank()) {
            return result;
        }
        String sql = """
                SELECT COL_NAME(fc.parent_object_id, fc.parent_column_id) AS fk_col,
                       OBJECT_NAME(fk.referenced_object_id) AS pk_table,
                       COL_NAME(fc.referenced_object_id, fc.referenced_column_id) AS pk_col
                FROM sys.foreign_keys AS fk
                INNER JOIN sys.foreign_key_columns AS fc ON fk.object_id = fc.constraint_object_id
                WHERE OBJECT_SCHEMA_NAME(fk.parent_object_id) = SCHEMA_NAME()
                  AND OBJECT_NAME(fk.parent_object_id) = ?
                """;
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String fkCol = rs.getString(1);
                    String pkTable = rs.getString(2);
                    String pkCol = rs.getString(3);
                    result.add(new DbForeignKey(
                            fkCol == null ? null : fkCol.toLowerCase(Locale.ROOT),
                            pkTable == null ? null : pkTable.toLowerCase(Locale.ROOT),
                            pkCol == null ? null : pkCol.toLowerCase(Locale.ROOT)));
                }
            }
        } catch (Exception e) {
            Log.warnf(e, "mssql importedKeys via sys.foreign_keys failed for %s; falling back to JDBC metadata",
                    table);
            return JdbcRepository.super.importedKeys(table);
        }
        return result;
    }

    @Override
    public String columnSql(ColumnSpec column) {
        if (column == null || column.kind() == null) {
            return null;
        }
        return escape(column.name()) + switch (column.kind()) {
            case SHORT_TEXT -> " nvarchar(200) null";
            case MEDIUM_TEXT -> " nvarchar(2048) null";
            case LONG_TEXT -> " nvarchar(max) null";
            case INTEGER -> " int";
            case DECIMAL -> " float";
            case BOOLEAN -> " bit not null default 0";
            case DATE -> " date null";
            case DATETIME -> " datetime2 null";
            case TIME -> " time null";
        };
    }

    @Override
    public String keyColumnSql(EntityQueryFacet query, int varcharLength) {
        return switch (KeyType.fromOrUserDefined(query.table_key_type())) {
            case UUID, SLUG -> escape(query.table_key()) + " nvarchar(" + varcharLength + ") NOT NULL";
            case AUTOINCREMENT -> escape(query.table_key()) + " int IDENTITY(1,1) NOT NULL";
            case USERDEFINED -> null;
        };
    }

    @Override
    public String addColumnSql(String table, String columnSql) {
        return "ALTER TABLE " + escape(table) + " ADD " + columnSql;
    }

    @Override
    public String renameColumnSql(String table, String from, String to) {
        // sp_rename 'table.old', 'new', 'COLUMN'
        return "EXEC sp_rename N'" + table + "." + from + "', N'" + to + "', N'COLUMN'";
    }

    @Override
    public String renameTableSql(String from, String to) {
        return "EXEC sp_rename N'" + from + "', N'" + to + "'";
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
