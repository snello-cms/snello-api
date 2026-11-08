package io.snello.service.repository.postgresql;

import io.quarkus.logging.Log;
import io.snello.api.service.JdbcRepository;
import io.snello.model.Condition;
import io.snello.model.facet.EntityQueryFacet;
import io.snello.model.pojo.ColumnSpec;
import io.snello.util.ConditionUtils;
import io.snello.util.ParamUtils;
import io.snello.util.SqlHelper;
import jakarta.ws.rs.core.MultivaluedMap;

import javax.sql.DataSource;
import java.sql.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.snello.management.DbConstants.*;
import static io.snello.service.repository.postgresql.PostgresqlConstants.*;

//@Singleton
//@Requires(property = DB_TYPE, value = "postgresql")
public class PostgresqlJdbcRepository implements JdbcRepository {

    private final DataSource dataSource;


    public PostgresqlJdbcRepository(DataSource dataSource) {
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
        return new String[]{
                "ALTER TABLE IF EXISTS metadatas ADD COLUMN IF NOT EXISTS ai_profile TEXT",
                "ALTER TABLE IF EXISTS fielddefinitions ADD COLUMN IF NOT EXISTS ai_profile TEXT"
        };
    }

    @Override
    public String[] creationQueries() {
        return new String[]{
                creationActionExtensions,
                upgradeActionsPhase,
                creationQueryMetadatas,
                creationQueryFieldDefinitions,
                upgradeFieldDefinitionsOrderNum,
                upgradeMetadataMissingColumns,
                upgradeDocumentsCreationDate,
                upgradeMetadataTextColumns,
                upgradeFieldDefinitionsMissingColumns,
                upgradeFieldDefinitionsTextColumns,
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
                creationAiServiceAccount
        };
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public long count(String table, String alias_condition, MultivaluedMap<String, String> httpParameters, List<Condition> conditions) throws Exception {
        StringBuffer where = new StringBuffer();
        StringBuilder select = new StringBuilder();
        List<Object> in = new LinkedList<>();
        select.append(COUNT_QUERY);
        if (alias_condition != null)
            where.append(alias_condition);
        boolean withCondition = false;
        try {
            withCondition = ConditionUtils.where(httpParameters, conditions, where, in);
        } catch (Exception e) {
            Log.info(e.getMessage());
        }
        if (!withCondition) {
            ParamUtils.where(httpParameters, where, in);
        }
        try (Connection connection = dataSource.getConnection()) {

            if (!where.isEmpty()) {
                where = new StringBuffer(_WHERE_).append(where);
            }
            Log.info("query: " + select + PostgresqlSqlUtils.escape(table) + where);
            try (PreparedStatement preparedStatement = connection.prepareStatement(select + PostgresqlSqlUtils.escape(table) + where)) {
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


    public long count(String select_query, MultivaluedMap<String, String> httpParameters, List<Condition> conditions) throws Exception {
        return 0;
    }

    public long count(String select_query) throws Exception {
        return 0;
    }

    public boolean exist(String table, String table_key, Object uuid) throws Exception {
        String select = COUNT_QUERY + PostgresqlSqlUtils.escape(table) + _WHERE_ + PostgresqlSqlUtils.escape(table_key) + "= ?";
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


    public List<Map<String, Object>> list(String table, String select_fields, String alias_condition, MultivaluedMap<String, String> httpParameters, List<Condition> conditions, String sort, int limit, int start) throws Exception {
        StringBuffer where = new StringBuffer();
        StringBuilder order_limit = new StringBuilder();
        StringBuilder select = new StringBuilder();
        List<Object> in = new LinkedList<>();
        select.append(_SELECT_);
        if (select_fields != null) {
            //"_SELECT_ * _FROM_ "
            select.append(select_fields);
        } else {
            select.append(_ALL_);
        }
        select.append(_FROM_);
        if (alias_condition != null && !alias_condition.trim().isEmpty()) {
            where.append(PostgresqlSqlUtils.escape(alias_condition));
        }

        String normalizedSort = sort == null ? null : sort.trim();
        if (normalizedSort != null && !normalizedSort.isEmpty()) {
            if (normalizedSort.contains("random")) {
                order_limit.append(_ORDER_BY_).append("RANDOM()");
            } else if (normalizedSort.contains(":")) {
                String[] sort_ = normalizedSort.split(":");
                order_limit.append(_ORDER_BY_).append(sort_[0]).append(" ").append(sort_[1]);
            } else {
                order_limit.append(_ORDER_BY_).append(normalizedSort);
            }
        }

        boolean withCondition = false;
        try {
            withCondition = ConditionUtils.where(httpParameters, conditions, where, in);
        } catch (Exception e) {
            Log.info(e.getMessage());
        }
        if (!withCondition) {
            ParamUtils.where(httpParameters, where, in);
        }
        if (start == 0 && limit == 0) {
            Log.info("no limits");
        } else {
            if (start > 0) {
                order_limit.append(_OFFSET_).append(_COND_);
                in.add(start);
            } else {
                order_limit.append(_OFFSET_).append(_COND_);
                in.add(0);
            }
            if (limit > 0) {
                order_limit.append(_LIMIT_).append(_COND_);
                in.add(limit);
            } else {
                order_limit.append(_LIMIT_).append(_COND_);
                in.add(10);
            }
        }
        try (Connection connection = dataSource.getConnection()) {

            if (!where.isEmpty()) {
                where = new StringBuffer(_WHERE_).append(where);
            }
            Log.info("LIST query: " + select.toString() + PostgresqlSqlUtils.escape(table) + where + order_limit);
            return PostgresqlSqlUtils.executeQueryList(connection, select.toString() + PostgresqlSqlUtils.escape(table) + where.toString() + order_limit.toString(), in);
        }

    }

    public List<Map<String, Object>> list(String query, MultivaluedMap<String, String> httpParameters, List<Condition> conditions, String sort, int limit, int start) throws Exception {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("query cannot be null or empty");
        }
        StringBuffer where = new StringBuffer();
        StringBuffer order_limit = new StringBuffer();
        StringBuffer select = new StringBuffer(query);
        List<Object> in = new LinkedList<>();

        String normalizedSort = sort == null ? null : sort.trim();
        if (normalizedSort != null && !normalizedSort.isEmpty()) {
            if (normalizedSort.contains(":")) {
                String[] sort_ = normalizedSort.split(":");
                order_limit.append(_ORDER_BY_).append(sort_[0]).append(" ").append(sort_[1]);
            } else {
                order_limit.append(_ORDER_BY_).append(normalizedSort);
            }
        }

        boolean withCondition = false;
        try {
            withCondition = ConditionUtils.where(httpParameters, conditions, where, in);
        } catch (Exception e) {
            Log.info(e.getMessage());
        }
        if (!withCondition) {
            ParamUtils.where(httpParameters, where, in);
        }
        if (start == 0 && limit == 0) {
            Log.info("no limits");
        } else {
            if (start > 0) {
                order_limit.append(_OFFSET_).append(_COND_);
                in.add(start);
            } else {
                order_limit.append(_OFFSET_).append(_COND_);
                in.add(0);
            }
            if (limit > 0) {
                order_limit.append(_LIMIT_).append(_COND_);
                in.add(limit);
            } else {
                order_limit.append(_LIMIT_).append(_COND_);
                in.add(10);
            }
        }
        try (Connection connection = dataSource.getConnection()) {
            if (!where.isEmpty() && !select.toString().contains(_WHERE_)) {
                where = new StringBuffer(_WHERE_).append(where);
            } else {
                where = new StringBuffer(where);
            }
            Log.info("LIST query: " + select.toString() + where + order_limit);
            return PostgresqlSqlUtils.executeQueryList(connection, select.toString() + where.toString() + order_limit.toString(), in);
        }

    }

    public List<Map<String, Object>> list(String query) throws Exception {
        List<Object> in = new LinkedList<>();
        try (Connection connection = dataSource.getConnection()) {
            Log.info("LIST query: " + query);
            return PostgresqlSqlUtils.executeQueryList(connection, query, in);
        }

    }

    public Map<String, Object> create(String table, String table_key, Map<String, Object> map) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String query = PostgresqlSqlUtils.create(table, map);
            Log.info("CREATE QUERY: " + query);
            PostgresqlSqlUtils.executeQueryCreate(connection, query, map, table_key);
        }
        return map;
    }

    public boolean query(String query, List<Object> values) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            Log.info("EXECUTE QUERY: " + query);
            return PostgresqlSqlUtils.executeQuery(connection, query, values);
        } catch (Exception e) {
            Log.error("error: ", e);
            return false;
        }

    }

    public Map<String, Object> update(String table, String table_key, Map<String, Object> map, String key) throws
            Exception {
        Map<String, Object> keys = new HashMap<>();
        List<Object> in = new LinkedList<>();
        keys.put(table_key, key);
        String query = PostgresqlSqlUtils.update(table, map, keys, in);
        try (Connection connection = dataSource.getConnection()) {
            Log.info("UPDATE QUERY: " + query);
            PostgresqlSqlUtils.executeQueryUpdate(connection, query, in);
        }
        return map;
    }

    public Map<String, Object> fetch(String select_fields, String table, String table_key, String uuid) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (select_fields == null) {
                select_fields = " * ";
            }
            Log.info("FETCH QUERY: " + "_SELECT_ * _FROM_ " + PostgresqlSqlUtils.escape(table) + " _WHERE_ " + table_key + " = ?");
            PreparedStatement preparedStatement = connection.prepareStatement(_SELECT_ + select_fields + _FROM_ + PostgresqlSqlUtils.escape(table)
                                                                              + _WHERE_ + PostgresqlSqlUtils.escape(table_key) + " = ?");
            preparedStatement.setObject(1, uuid);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return PostgresqlSqlUtils.single(resultSet);
            }
        }
    }

    public boolean delete(String table, String table_key, String uuid) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            Log.info("DELETE QUERY: " + DELETE_FROM + table + _WHERE_ + table_key + " = ? ");
            PreparedStatement preparedStatement = connection.prepareStatement(DELETE_FROM + PostgresqlSqlUtils.escape(table) + _WHERE_
                                                                              + PostgresqlSqlUtils.escape(table_key) + " = ?");
            preparedStatement.setObject(1, uuid);
            int result = preparedStatement.executeUpdate();
            return result > 0;
        }
    }

    /**
     * Runs each statement independently so a single failing (e.g. legacy-schema) statement
     * does not prevent the remaining ones from being applied.
     */
    public void batch(String[] queries) throws Exception {
        Log.info("BATCH start, statements=" + queries.length);
        int failed = 0;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String query : queries) {
                Log.info("BATCH QUERY: " + query);
                try {
                    statement.execute(query);
                } catch (SQLException e) {
                    failed++;
                    Log.error("BATCH QUERY failed (continuing): " + e.getMessage() + " -- query: " + query);
                }
            }
        }
        Log.info("BATCH done, failed=" + failed + "/" + queries.length);
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
            if (statement != null)
                statement.close();
        }
        return false;
    }

    public boolean verifyTable(String tableName) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement(SHOW_TABLES + "('" + tableName + "')");
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    Object resul = resultSet.getObject(1);
                    if (resul != null)
                        return true;
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
        return PostgresqlSqlUtils.escape(name);
    }

    @Override
    public String columnSql(ColumnSpec column) {
        if (column == null || column.kind() == null) {
            return null;
        }
        return escape(column.name()) + switch (column.kind()) {
            case SHORT_TEXT -> " varchar(200) default null";
            case MEDIUM_TEXT -> " varchar(2048) default null";
            case LONG_TEXT -> " text default null";
            case INTEGER -> " NUMERIC(10)";
            case DECIMAL -> " DOUBLE PRECISION";
            case BOOLEAN -> " boolean default false";
            case DATE -> " date default null";
            case DATETIME -> " TIMESTAMPTZ default null";
            case TIME -> " time default null";
        };
    }

    @Override
    public String keyColumnSql(EntityQueryFacet query, int varcharLength) {
        return switch (query.table_key_type() == null ? "userdefined" : query.table_key_type()) {
            case "uuid", "slug" -> escape(query.table_key()) + " VARCHAR(" + varcharLength + ") NOT NULL";
            case "autoincrement" -> escape(query.table_key()) + " SERIAL";
            // user-defined: the key column is one of the declared fields
            default -> null;
        };
    }

}
