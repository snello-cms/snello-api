package io.snello.service.repository.h2;

import io.quarkus.logging.Log;
import io.snello.api.service.JdbcRepository;
import io.snello.model.Condition;
import io.snello.model.FieldDefinition;
import io.snello.model.Metadata;
import io.snello.util.ConditionUtils;
import io.snello.util.FieldDefinitionUtils;
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
import static io.snello.service.repository.h2.H2Constants.*;

public class H2JdbcRepository implements JdbcRepository {

    private final DataSource dataSource;

    public H2JdbcRepository(DataSource dataSource) {
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
    public String[] creationQueries() {
        return new String[]{
                creationQueryActionExtensions,
                creationQueryMetadatas,
                creationQueryFieldDefinitions,
                creationQueryConditions,
                creationQueryDocuments,
                creationQuerySelectQueries,
                creationLinksQueries,
                creationChatInteractions,
                upgradeChatInteractionsTextColumns,
                creationAiTools
        };
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public long count(String table, String alias_condition, MultivaluedMap<String, String> httpParameters, List<Condition> conditions) throws Exception {
        StringBuffer where = new StringBuffer();
        StringBuffer select = new StringBuffer();
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

        try (
                Connection connection = dataSource.getConnection()) {

            if (where.length() > 0) {
                where = new StringBuffer(_WHERE_).append(where);
            }
            Log.info("query: " + select + H2SqlUtils.escape(table) + where);
            try (PreparedStatement preparedStatement = connection.prepareStatement(select + H2SqlUtils.escape(table) + where)) {
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
        String select = COUNT_QUERY + H2SqlUtils.escape(table) + _WHERE_ + H2SqlUtils.escape(table_key) + "= ?";
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
            where.append(H2SqlUtils.escape(alias_condition));
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

//        ParamUtils.where(httpParameters, where, in);
//        ConditionUtils.where(httpParameters, conditions, where, in);

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
            Log.info("LIST query: " + select.toString() + H2SqlUtils.escape(table) + where + order_limit);
            return H2SqlUtils.executeQueryList(connection, select.toString() + H2SqlUtils.escape(table) + where.toString() + order_limit.toString(), in);
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
            if (where.length() > 0 && !select.toString().contains(_WHERE_)) {
                where = new StringBuffer(_WHERE_).append(where);
            } else {
                where = new StringBuffer(where);
            }
            Log.info("LIST query: " + select.toString() + where + order_limit);
            return H2SqlUtils.executeQueryList(connection, select.toString() + where.toString() + order_limit.toString(), in);
        }

    }

    public List<Map<String, Object>> list(String query) throws Exception {
        List<Object> in = new LinkedList<>();
        try (Connection connection = dataSource.getConnection()) {
            Log.info("LIST query: " + query);
            return H2SqlUtils.executeQueryList(connection, query, in);
        }

    }

    public Map<String, Object> create(String table, String table_key, Map<String, Object> map) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String query = H2SqlUtils.create(table, map);
            Log.info("CREATE QUERY: " + query);
            H2SqlUtils.executeQueryCreate(connection, query, map, table_key);
        }
        return map;
    }

    public boolean query(String query, List<Object> values) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            Log.info("EXECUTE QUERY: " + query);
            return H2SqlUtils.executeQuery(connection, query, values);
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
        String query = H2SqlUtils.update(table, map, keys, in);
        try (Connection connection = dataSource.getConnection()) {
            Log.info("UPDATE QUERY: " + query);
            H2SqlUtils.executeQueryUpdate(connection, query, in);
        }
        return map;
    }

    public Map<String, Object> fetch(String select_fields, String table, String table_key, String uuid) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (select_fields == null) {
                select_fields = " * ";
            }
            Log.info("FETCH QUERY: " + "_SELECT_ * _FROM_ " + H2SqlUtils.escape(table) + " _WHERE_ " + table_key + " = ?");
            PreparedStatement preparedStatement = connection.prepareStatement(_SELECT_ + select_fields + _FROM_ + H2SqlUtils.escape(table)
                                                                              + _WHERE_ + H2SqlUtils.escape(table_key) + " = ?");
            preparedStatement.setObject(1, uuid);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return H2SqlUtils.single(resultSet);
            }
        }
    }

    public boolean delete(String table, String table_key, String uuid) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            Log.info("DELETE QUERY: " + DELETE_FROM + table + _WHERE_ + table_key + " = ? ");
            PreparedStatement preparedStatement = connection.prepareStatement(DELETE_FROM + H2SqlUtils.escape(table) + _WHERE_
                                                                              + H2SqlUtils.escape(table_key) + " = ?");
            preparedStatement.setObject(1, uuid);
            int result = preparedStatement.executeUpdate();
            return result > 0;
        }
    }

    public void batch(String[] queries) throws Exception {
        final int batchSize = 5;
        int count = 0;
        try (Connection connection = dataSource.getConnection()) {
            Statement statement = connection.createStatement();
            for (String query : queries) {
                Log.info("BATCH QUERY: " + query);
                statement.addBatch(query);
                if (++count % batchSize == 0) {
                    statement.executeBatch();
                }
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
            PreparedStatement preparedStatement = connection.prepareStatement(SHOW_TABLES);
            List<Map<String, Object>> list = null;
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                list = H2SqlUtils.list(resultSet);
            }
            for (Map<String, Object> map : list) {
                if (!map.containsKey("table_name")) {
                    continue;
                }
                String table_name_found = (String) map.get("table_name");
                if (table_name_found.toLowerCase().equals(tableName.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
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
//                map = H2SqlUtils.single(resultSet);
//            }
//        }
//        if (map == null) {
//            Log.info("password not found for username: " + username);
//            throw new Exception("invalid username/password");
//        }
//        String passwordOnDb = (String) map.get(PWD_LOWERCASE);
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
        return H2SqlUtils.escape(name);
    }

    @Override
    public String fieldDefinition2Sql(FieldDefinition fieldDefinition) throws Exception {
        return H2FieldDefinitionUtils.sql(fieldDefinition);
    }

    @Override
    public String createTableSql(Metadata metadata, List<FieldDefinition> fields, List<String> joiQueries, List<Condition> conditions) throws Exception {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata cannot be null");
        }
        StringBuffer sb = new StringBuffer(" CREATE TABLE " + escape(metadata.table_name) + " (");
        String tableKeyType = metadata.table_key_type == null ? "userdefined" : metadata.table_key_type;
        if ("autoincrement".equals(tableKeyType)) {
            sb.append(escape(metadata.table_key) + " int NOT NULL AUTO_INCREMENT ");
        } else {
            sb.append(escape(metadata.table_key) + " VARCHAR(50) NOT NULL ");
        }
        List<FieldDefinition> safeFields = fields == null ? List.of() : fields;
        for (FieldDefinition fieldDefinition : safeFields) {
            if (fieldDefinition == null) {
                continue;
            }
            if (fieldDefinition.sql_definition != null && !fieldDefinition.sql_definition.trim().isEmpty()) {
                sb.append(",").append(fieldDefinition.sql_definition);
            } else {
                String columnSql = fieldDefinition2Sql(fieldDefinition);
                if (columnSql == null || columnSql.trim().isEmpty()) {
                    throw new IllegalArgumentException("Unsupported field type in createTableSql: name='" + fieldDefinition.name + "', type='" + fieldDefinition.type + "'");
                }
                sb.append(",").append(columnSql);
            }
                if (FieldDefinitionUtils.isMultiJoinType(fieldDefinition.type)) {
                String join_table_name = metadata.table_name + "_" + fieldDefinition.join_table_name;
                String table_id = metadata.table_name + "_id";
                String join_table_id = fieldDefinition.join_table_name + "_id";
                if (joiQueries != null) {
                    joiQueries.add(String.format(getJoinTableQuery(), metadata.table_name + "_" + fieldDefinition.join_table_name,
                            metadata.table_name + "_id", fieldDefinition.join_table_name + "_id"));
                }
                Condition condition = new Condition();
                condition.metadata_multijoin_uuid = metadata.uuid;
                condition.uuid = java.util.UUID.randomUUID().toString();
                condition.metadata_name = fieldDefinition.join_table_name;
                condition.metadata_uuid = ""; //?? dove lo dovrei prendere??
                condition.condition = metadata.table_name + "_id_nn && join_table_nn";
                condition.query_params = metadata.table_name + "_id";
                condition.sub_query = fieldDefinition.join_table_key + " in (select " + join_table_id + " from " + join_table_name + " where " + table_id + " = ?)";
                if (conditions != null) {
                    conditions.add(condition);
                }
            }
        }
        sb.append(", PRIMARY KEY (" + escape(metadata.table_key) + ")").append(") ;");
        Log.info("CREATION TABLE QUERY: " + sb.toString());
        return sb.toString();
    }
}
