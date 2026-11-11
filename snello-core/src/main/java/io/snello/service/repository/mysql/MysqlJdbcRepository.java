package io.snello.service.repository.mysql;

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
import static io.snello.service.repository.mysql.MysqlConstants.*;

public class MysqlJdbcRepository implements JdbcRepository {

    private final DataSource dataSource;

    public MysqlJdbcRepository(DataSource dataSource) {
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
        // MySQL 8 has no ADD COLUMN IF NOT EXISTS: re-runs fail with 1060, see isAlreadyApplied
        return new String[]{
                "ALTER TABLE metadatas ADD COLUMN ai_profile TEXT",
                "ALTER TABLE fielddefinitions ADD COLUMN ai_profile TEXT",
                // row-level security columns existed only in the PostgreSQL schema
                "ALTER TABLE metadatas ADD COLUMN api_protected BOOLEAN NOT NULL DEFAULT FALSE",
                "ALTER TABLE metadatas ADD COLUMN username_field varchar(255) DEFAULT NULL",
                // row-level security for select queries (same contract as metadatas)
                "ALTER TABLE selectqueries ADD COLUMN api_protected BOOLEAN NOT NULL DEFAULT FALSE",
                "ALTER TABLE selectqueries ADD COLUMN username_field varchar(255) DEFAULT NULL"
        };
    }

    @Override
    public boolean isAlreadyApplied(Exception e) {
        // ER_DUP_FIELDNAME
        return e instanceof java.sql.SQLException sql && sql.getErrorCode() == 1060;
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
                creationPrintTemplates
        };
    }

    public long count(String table, String alias_condition, MultivaluedMap<String, String> httpParameters, List<Condition> conditions) throws Exception {
        StringBuffer where = new StringBuffer();
        StringBuffer select = new StringBuffer();
        List<Object> in = new LinkedList<>();
        select.append(COUNT_QUERY);
        if (alias_condition != null)
            where.append(alias_condition);
        // conditions AND generic filters (owner filter included): see ConditionUtils.whereWithParams
        ConditionUtils.whereWithParams(httpParameters, conditions, where, in);
        try (
                Connection connection = dataSource.getConnection()) {

            if (where.length() > 0) {
                where = new StringBuffer(_WHERE_).append(where);
            }
            Log.info("query: " + select + MysqlSqlUtils.escape(table) + where);
            try (PreparedStatement preparedStatement = connection.prepareStatement(select + MysqlSqlUtils.escape(table) + where)) {
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
        StringBuffer where = new StringBuffer();
        StringBuffer select = new StringBuffer(select_query);
        List<Object> in = new LinkedList<>();
        // conditions AND generic filters (owner filter included): see ConditionUtils.whereWithParams
        ConditionUtils.whereWithParams(httpParameters, conditions, where, in);
        try (
                Connection connection = dataSource.getConnection()) {

            if (where.length() > 0) {
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
            if (where.length() > 0) {
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
        String select = COUNT_QUERY + MysqlSqlUtils.escape(table) + _WHERE_ + MysqlSqlUtils.escape(table_key) + "= ?";
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
        StringBuffer order_limit = new StringBuffer();
        StringBuffer select = new StringBuffer();
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
            where.append(MysqlSqlUtils.escape(alias_condition));
        }

        String normalizedSort = sort == null ? null : sort.trim();
        if (normalizedSort != null && !normalizedSort.isEmpty()) {
            if (normalizedSort.contains(":")) {
                String[] sort_ = normalizedSort.split(":");
                order_limit.append(_ORDER_BY_).append(sort_[0]).append(" ").append(sort_[1]);
            } else {
                order_limit.append(_ORDER_BY_).append(normalizedSort);
            }
        }


        // conditions AND generic filters (owner filter included): see ConditionUtils.whereWithParams
        ConditionUtils.whereWithParams(httpParameters, conditions, where, in);
        if (start == 0 && limit == 0) {
            Log.info("no limits");
        } else {
            if (start > 0) {
                order_limit.append(_LIMIT_).append(" ? ");
                in.add(start);
            } else {
                order_limit.append(_LIMIT_).append(" ? ");
                in.add(0);
            }
            if (limit > 0) {
                order_limit.append(",").append(" ? ");
                in.add(limit);
            } else {
                order_limit.append(", ? ");
                in.add(10);
            }
        }
        try (Connection connection = dataSource.getConnection()) {

            if (where.length() > 0) {
                where = new StringBuffer(_WHERE_).append(where);
            }
            Log.info("LIST query: " + select.toString() + MysqlSqlUtils.escape(table) + where + order_limit);
            return MysqlSqlUtils.executeQueryList(connection, select.toString() + MysqlSqlUtils.escape(table) + where.toString() + order_limit.toString(), in);
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

        // conditions AND generic filters (owner filter included): see ConditionUtils.whereWithParams
        ConditionUtils.whereWithParams(httpParameters, conditions, where, in);
        if (start == 0 && limit == 0) {
            Log.info("no limits");
        } else {
            if (start > 0) {
                order_limit.append(_LIMIT_).append(" ? ");
                in.add(start);
            } else {
                order_limit.append(_LIMIT_).append(" ? ");
                in.add(0);
            }
            if (limit > 0) {
                order_limit.append(",").append(" ? ");
                in.add(limit);
            } else {
                order_limit.append(", ? ");
                in.add(10);
            }
        }
        try (Connection connection = dataSource.getConnection()) {
            if (where.length() > 0) {
                where = new StringBuffer(io.snello.util.SqlWhere.joiner(select.toString())).append(where);
            }
            Log.info("LIST query: " + select.toString() + where + order_limit);
            return MysqlSqlUtils.executeQueryList(connection, select.toString() + where.toString() + order_limit.toString(), in);
        }

    }

    public List<Map<String, Object>> list(String query) throws Exception {
        List<Object> in = new LinkedList<>();
        try (Connection connection = dataSource.getConnection()) {
            Log.info("LIST query: " + query);
            return MysqlSqlUtils.executeQueryList(connection, query, in);
        }

    }

    public Map<String, Object> create(String table, String table_key, Map<String, Object> map) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String query = MysqlSqlUtils.create(table, map);
            Log.info("CREATE QUERY: " + query);
            MysqlSqlUtils.executeQueryCreate(connection, query, map, table_key);
        }
        return map;
    }

    public boolean query(String query, List<Object> values) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            Log.info("EXECUTE QUERY: " + query);
            return MysqlSqlUtils.executeQuery(connection, query, values);
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
        String query = MysqlSqlUtils.update(table, map, keys, in);
        try (Connection connection = dataSource.getConnection()) {
            Log.info("UPDATE QUERY: " + query);
            MysqlSqlUtils.executeQueryUpdate(connection, query, in);
        }
        return map;
    }

    public Map<String, Object> fetch(String select_fields, String table, String table_key, String uuid) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (select_fields == null) {
                select_fields = " * ";
            }
            Log.info("FETCH QUERY: " + "_SELECT_ * _FROM_ " + MysqlSqlUtils.escape(table) + " _WHERE_ " + table_key + " = ?");
            PreparedStatement preparedStatement = connection.prepareStatement(_SELECT_ + select_fields + _FROM_ + MysqlSqlUtils.escape(table)
                                                                              + _WHERE_ + MysqlSqlUtils.escape(table_key) + " = ?");
            preparedStatement.setObject(1, uuid);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return MysqlSqlUtils.single(resultSet);
            }
        }
    }

    public boolean delete(String table, String table_key, String uuid) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            Log.info("DELETE QUERY: " + DELETE_FROM + table + _WHERE_ + table_key + " = ? ");
            PreparedStatement preparedStatement = connection.prepareStatement(DELETE_FROM + MysqlSqlUtils.escape(table) + _WHERE_
                                                                              + MysqlSqlUtils.escape(table_key) + " = ?");
            preparedStatement.setObject(1, uuid);
            int result = preparedStatement.executeUpdate();
            return result > 0;
        }
    }

    public void batch(String[] queries) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            Statement statement = connection.createStatement();
            for (String query : queries) {
                Log.info("BATCH QUERY: " + query);
                statement.addBatch(query);
            }
            statement.executeBatch();
            statement.close();
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
            if (statement != null)
                statement.close();
        }
        return false;
    }

    public boolean verifyTable(String tableName) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            Statement statement = connection.createStatement();
            PreparedStatement preparedStatement = connection.prepareStatement(
                    SHOW_TABLES_INIT
                    //+ getDbName()
                    + SHOW_TABLES_END);
            preparedStatement.setObject(1, tableName);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getDbName() throws Exception {
        ResultSet rs = dataSource.getConnection().getMetaData().getSchemas();
        while (rs.next()) {
            Log.info(rs.getString(1));
            return rs.getString(1);
        }
        throw new Exception();
    }

//    @Override
//    public SecurityIdentity login(String username, String password) throws Exception {
//        if (username == null) {
//            throw new Exception("login must contain username in 'username' field");
//        }
//        if (password == null) {
//            throw new Exception("login must contain password in 'password' field");
//        }
//        Map<String, Object> map = null;
//        try (Connection connection = dataSource.getConnection()) {
//            Log.info("login QUERY: " + LOGIN_QUERY);
//            PreparedStatement preparedStatement = connection.prepareStatement(LOGIN_QUERY);
//            preparedStatement.setObject(1, username);
//            try (ResultSet resultSet = preparedStatement.executeQuery()) {
//                map = MysqlSqlUtils.single(resultSet);
//            }
//        }
//        if (map == null) {
//            Log.info("password not found for username: " + username);
//            throw new Exception("invalid username/password");
//        }
//        String passwordOnDb = (String) map.get("password");
//        String encrPassword = PasswordUtils.createPassword(password);
//        if (encrPassword.equals(passwordOnDb)) {
//            return new UserDetails(username, getRoles(username));
//        }
//        throw new Exception("Failure in authentication");
//    }


    @Override
    public String getJoinTableQuery() {
        return joinTableQuery;
    }

    @Override
    public String escape(String name) {
        return MysqlSqlUtils.escape(name);
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
            case INTEGER -> " int(10)";
            case DECIMAL -> " DOUBLE";
            case BOOLEAN -> " boolean default false";
            case DATE -> " date default null";
            case DATETIME -> " datetime default null";
            case TIME -> " time default null";
        };
    }

    @Override
    public String keyColumnSql(EntityQueryFacet query, int varcharLength) {
        return switch (query.table_key_type() == null ? "userdefined" : query.table_key_type()) {
            case "uuid", "slug" -> escape(query.table_key()) + " VARCHAR(" + varcharLength + ") NOT NULL";
            case "autoincrement" -> escape(query.table_key()) + " int NOT NULL AUTO_INCREMENT";
            // user-defined: the key column is one of the declared fields
            default -> null;
        };
    }

    @Override
    public String createTableTail() {
        return ") ENGINE=INNODB;";
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
