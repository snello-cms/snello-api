package io.snello.cms.repository;

import io.micronaut.discovery.event.ServiceStartedEvent;
import io.micronaut.http.HttpParameters;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.scheduling.annotation.Async;
import io.snello.cms.model.Condition;
import io.snello.cms.util.ConditionUtils;
import io.snello.cms.util.ParamUtils;
import io.snello.cms.util.SqlHelper;
import io.snello.cms.util.SqlUtils;
import io.snello.cms.model.Document;
import io.snello.cms.model.FieldDefinition;
import io.snello.cms.model.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Singleton
public class JdbcRepository {

    DataSource dataSource;
    Logger logger = LoggerFactory.getLogger(getClass());


    public JdbcRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @EventListener
    @Async
    public void onLoad(final ServiceStartedEvent event) {
        logger.info("Creation queries at startup: " + event.toString());
        try {
            batch(new String[]{Metadata.creationQuery, FieldDefinition.creationQuery, Condition.creationQuery, Document.creationQuery});
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public long count(String table, String alias_condition, HttpParameters httpParameters,List<Condition> conditions) throws Exception {
        StringBuffer where = new StringBuffer();
        StringBuffer select = new StringBuffer();
        List<Object> in = new LinkedList<>();
        select.append(" SELECT COUNT(*) AS SIZE_OF FROM ");
        if (alias_condition != null)
            where.append(alias_condition);
        ParamUtils.where(httpParameters, where, in);
        ConditionUtils.where(httpParameters, conditions, where, in);
        try (
                Connection connection = dataSource.getConnection()) {

            if (where.length() > 0) {
                where = new StringBuffer(" WHERE ").append(where);
            }
            logger.info("query: " + select + table + where);
            try (PreparedStatement preparedStatement = connection.prepareStatement(select + table + where)) {
                SqlHelper.fillStatement(preparedStatement, in);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        long count = resultSet.getLong("SIZE_OF");
                        logger.info("count:" + count);
                        return count;
                    }
                }
            }
        }
        return 0;
    }

    public boolean exist(String table, String table_key, Object uuid) throws Exception {
        String select = " SELECT COUNT(*) AS SIZE_OF FROM " + table + " WHERE " + table_key + "= ?";
        List<Object> in = new LinkedList<>();
        in.add(uuid);
        try (Connection connection = dataSource.getConnection()) {
            logger.info("query: " + select);
            try (PreparedStatement preparedStatement = connection.prepareStatement(select)) {
                SqlHelper.fillStatement(preparedStatement, in);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        long count = resultSet.getLong("SIZE_OF");
                        logger.info("exist:" + (count > 0));
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


    public List<Map<String, Object>> list(String table, String select_fields, String alias_condition, HttpParameters httpParameters, List<Condition> conditions, String sort, int limit, int start) throws Exception {
        StringBuffer where = new StringBuffer();
        StringBuffer order_limit = new StringBuffer();
        StringBuffer select = new StringBuffer();
        List<Object> in = new LinkedList<>();
        select.append(" SELECT ");
        if (select_fields != null) {
            //"SELECT * FROM "
            select.append(select_fields);
        } else {
            select.append(" * ");
        }
        select.append(" FROM ");
        if (alias_condition != null && !alias_condition.trim().isEmpty()) {
            where.append(alias_condition);
        }

        if (sort != null) {
            if (sort.contains(":")) {
                String[] sort_ = sort.split(":");
                order_limit.append(" ORDER BY ").append(sort_[0]).append(" ").append(sort_[1]);
            } else {
                order_limit.append(" ORDER BY ").append(sort);
            }
        }

        ParamUtils.where(httpParameters, where, in);
        ConditionUtils.where(httpParameters, conditions, where, in);
        if (start == 0 && limit == 0) {
            logger.info("no limits");
        } else {
            if (start > 0) {
                order_limit.append(" LIMIT ").append(" ? ");
                in.add(start);
            } else {
                order_limit.append(" LIMIT ").append(" ? ");
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
                where = new StringBuffer(" WHERE ").append(where);
            }
            logger.info("LIST query: " + select + table + where + order_limit);
            return SqlUtils.executeQueryList(connection, select + table + where + order_limit, in);
        }

    }

    public Map<String, Object> create(String table, String table_key, Map<String, Object> map) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String query = SqlUtils.create(table, map);
            logger.info("CREATE QUERY: " + query);
            SqlUtils.executeQueryCreate(connection, query, map, table_key);
        }
        return map;
    }


    public Map<String, Object> update(String table, String table_key, Map<String, Object> map, String key) throws
            Exception {
        Map<String, Object> keys = new HashMap<>();
        List<Object> in = new LinkedList<>();
        keys.put(table_key, key);
        String query = SqlUtils.update(table, map, keys, in);
        try (Connection connection = dataSource.getConnection()) {
            logger.info("UPDATE QUERY: " + query);
            SqlUtils.executeQueryUpdate(connection, query, in);
        }
        return map;
    }

    public Map<String, Object> fetch(String table, String table_key, String uuid) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            logger.info("FETCH QUERY: " + "SELECT * FROM " + table + " WHERE " + table_key + " = ?");
            PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM " + table + " WHERE " + table_key + " = ?");
            preparedStatement.setObject(1, uuid);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return SqlUtils.single(resultSet);
            }
        }
    }

    public boolean delete(String table, String table_key, String uuid) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            logger.info("DELETE QUERY: " + "DELETE FROM " + table + " WHERE " + table_key + " = ?");
            PreparedStatement preparedStatement = connection.prepareStatement("DELETE FROM " + table + " WHERE " + table_key + " = ?");
            preparedStatement.setObject(1, uuid);
            int result = preparedStatement.executeUpdate();
            return result > 0;
        }
    }

    public void batch(String[] queries) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            Statement statement = connection.createStatement();
            for (String query : queries) {
                logger.info("BATCH QUERY: " + query);
                statement.addBatch(query);
            }
            statement.executeBatch();
            statement.close();
        }
    }

    public boolean verifyTable(String tableName) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            Statement statement = connection.createStatement();
            PreparedStatement preparedStatement = connection.prepareStatement("SHOW TABLES FROM `matcms` LIKE ?");
            preparedStatement.setObject(1, tableName);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return true;
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        }
        return false;
    }

}
