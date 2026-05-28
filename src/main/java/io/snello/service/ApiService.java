package io.snello.service;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.security.identity.SecurityIdentity;
import io.snello.model.Condition;
import io.snello.model.FieldDefinition;
import io.snello.model.Metadata;
import io.snello.model.SelectQuery;
import io.snello.model.events.DbCreatedEvent;
import io.snello.api.service.JdbcRepository;
import io.snello.service.actions.ActionEvent;
import io.snello.util.FieldDefinitionUtils;
import io.snello.util.ParamUtils;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.snello.management.AppConstants.*;
import static io.snello.service.repository.mysql.MysqlConstants.DROP_TABLE;
import static io.snello.service.repository.mysql.MysqlConstants.TRUNCATE_TABLE;

@Singleton
public class ApiService {

    @Inject
    MetadataService metadataService;

    @Inject
    JdbcRepository jdbcRepository;

    @Inject
    Event<DbCreatedEvent> eventCreatedPublisher;

    @Inject
    SecurityIdentity securityIdentity;

    public ApiService() {
    }

    public void onLoad(@Observes StartupEvent event) {
        jdbcRepository.onLoad();
        eventCreatedPublisher.fireAsync(new DbCreatedEvent());
    }

    public Metadata metadata(String metadata_name) throws Exception {
        Metadata metadata = metadataService.metadataMap().get(metadata_name);
        return metadata;
    }

    public Metadata metadataWithFields(String metadata_name) throws Exception {
        Metadata metadata = metadataService.metadataMap().get(metadata_name);
        if (metadata == null) {
            return null;
        }
        metadata.fields = metadataService.fielddefinitions(metadata.table_name);
        return metadata;
    }

    public boolean isPassivable(String metadata_name) throws Exception {
        Metadata metadata = metadataService.metadataMap().get(metadata_name);
        if (metadata == null) {
            throw new Exception("metadata not found for table: " + metadata_name);
        }
        metadata.fields = metadataService.fielddefinitions(metadata.table_name);
        return isPassivable(metadata.fields);
    }

    public boolean isPassivable(List<FieldDefinition> fields) throws Exception {
        if (fields == null || fields.isEmpty()) return false;
        for (FieldDefinition fd : fields) {
            if ("passivation".equals(fd.input_type)) {
                return true;
            }
        }
        return false;
    }

    public String getPassivableField(List<FieldDefinition> fields) throws Exception {
        for (FieldDefinition fd : fields) {
            if ("passivation".equals(fd.input_type)) {
                return fd.name;
            }
        }
        return null;
    }

    public String slugField(String metadata_name) throws Exception {
        Metadata metadata = metadataService.metadataMap().get(metadata_name);
        if (metadata == null) {
            throw new Exception("metadata not found for table: " + metadata_name);
        }
        return metadata.table_key_addition;
    }

    public String table_key(String metadata_name) throws Exception {
        Metadata metadata = metadataService.metadataMap().get(metadata_name);
        if (metadata == null) {
            throw new Exception("metadata not found for table: " + metadata_name);
        }
        return metadata.table_key;
    }

    public long count(String table, UriInfo uriInfo) throws Exception {
        MultivaluedMap<String, String> queryParameters = uriInfo != null
                ? uriInfo.getQueryParameters()
                : null;
        return count(table, queryParameters);
    }

    public long count(String table, MultivaluedMap<String, String> queryParameters) throws Exception {
        String alias_condition = null;
        List<Condition> conditions = null;
        String scq = null;
        var exist = metadataService.selectqueryMap().containsKey(table);
        SelectQuery selectQuery = metadataService.selectqueryMap().get(table);
        if (selectQuery != null) {
            scq = selectQuery.select_query_count;
            Log.info("the table: " + table + " is selectquery? " + exist + ", is count? " + scq);
            if (exist && scq != null && !scq.isBlank()) {
                if (selectQuery.with_params)
                    return jdbcRepository.count(scq, queryParameters,
                            conditions);
                else
                    return jdbcRepository.count(scq);
            }
        }

        if (metadataService.metadataMap().containsKey(table)) {
            conditions = metadataService.conditionsMap().get(table);
            Metadata metadata = metadataService.metadataMap().get(table);
            if (metadata.alias_table != null && !metadata.alias_table.trim().isEmpty()) {
                table = metadata.alias_table;
                alias_condition = metadata.alias_condition;
            }
        }
        return jdbcRepository.count(table, alias_condition, queryParameters, conditions);
    }

    public boolean exist(String table, String table_key, Object uuid) throws Exception {
        return jdbcRepository.exist(table, table_key, uuid);
    }

    public List<Map<String, Object>> list(String table, MultivaluedMap<String, String> httpParameters, String sort,
            int limit,
            int start) throws Exception {
        // select fields:
        String select_fields = ParamUtils.select_fields(httpParameters);
        String alias_condition = null;
        List<Condition> conditions = null;
        if (metadataService.selectqueryMap().containsKey(table)) {
            SelectQuery selectQuery = metadataService.selectqueryMap.get(table);
            if (selectQuery.with_params) {
                return jdbcRepository.list(selectQuery.select_query, httpParameters, conditions, sort, limit, start);
            } else {
                return jdbcRepository.list(selectQuery.select_query);
            }
        }
        if (metadataService.metadataMap().containsKey(table)) {
            Metadata metadata = metadataService.metadataMap().get(table);
            conditions = metadataService.conditionsMap().get(table);
            if (select_fields == null) {
                if (metadata.select_fields != null && !metadata.select_fields.trim().isEmpty()) {
                    // "_SELECT_ * _FROM_ "
                    select_fields = metadata.select_fields;
                }
            } else {
                Log.info("uso i httpParameters:" + select_fields);
            }
            if (metadata.alias_table != null && !metadata.alias_table.trim().isEmpty()) {
                table = metadata.alias_table;
                alias_condition = metadata.alias_condition;
            }
            if (sort == null || sort.isEmpty()) {
                if (metadata.order_by != null && !metadata.order_by.trim().isEmpty()) {
                    sort = metadata.order_by;
                }
            }

        }
        return jdbcRepository.list(table, select_fields, alias_condition, httpParameters, conditions, sort, limit,
                start);
    }

    @ActionEvent
    public Map<String, Object> create(String table, Map<String, Object> map, String table_key) throws Exception {
        table = initTable(table);
        table_key = metadataService.initTableKey(table, table_key);
        return jdbcRepository.create(table, table_key, map);
    }

    public Map<String, Object> createFromMap(String table, Map<String, Object> map) throws Exception {
        return jdbcRepository.create(table, UUID, map);
    }

    public List<Map<String, Object>> list(String query) throws Exception {
        return jdbcRepository.list(query);
    }

    @ActionEvent
    public Map<String, Object> merge(String table, Map<String, Object> map, String key, String table_key)
            throws Exception {
        table = initTable(table);
        table_key = metadataService.initTableKey(table, table_key);
        return jdbcRepository.update(table, table_key, map, key);
    }

    public boolean truncateTable(String uuid) throws Exception {
        Metadata metadata = metadataService.byUUid(uuid);
        if (metadata == null) {
            return false;
        }
        String table;
        if (metadata.alias_table != null && !metadata.alias_table.trim().isEmpty()) {
            table = metadata.alias_table;
        }
        table = metadata.table_name;
        return jdbcRepository.query(TRUNCATE_TABLE + table, null);
    }

    public boolean deleteTable(String uuid) throws Exception {
        Metadata metadata = metadataService.byUUid(uuid);
        if (metadata == null) {
            return false;
        }
        metadata.fields = metadataService.fielddefinitions(metadata.table_name);
        List<FieldDefinition> fields = metadata.fields == null ? List.of() : metadata.fields;
        for (FieldDefinition fd : fields) {
            if (FieldDefinitionUtils.isMultiJoinType(fd.type)) {
                String join_table_name = metadata.table_name + "_" + fd.join_table_name;
                jdbcRepository.query(DROP_TABLE + join_table_name, null);
            }
        }
        // DEVO ELIMINARE TUTTE LE CONDITIONS COLLEGATE
        jdbcRepository.delete(CONDITIONS, "metadata_multijoin_uuid", metadata.uuid);
        if (metadata.already_exist) {
            throw new Exception("metadata was already_exist: we can't destroy!");
        }
        String table;
        if (metadata.alias_table != null && !metadata.alias_table.trim().isEmpty()) {
            table = metadata.alias_table;
        }
        table = metadata.table_name;
        return jdbcRepository.query(DROP_TABLE + table, null);
    }

    public Map<String, Object> createIfNotExists(String table, Map<String, Object> map, String table_key)
            throws Exception {
        alreadyExists(table);
        return jdbcRepository.create(table, table_key, map);
    }

    public Map<String, Object> mergeIfNotExists(String table, Map<String, Object> map, String key, String table_key)
            throws Exception {
        alreadyExists(table);
        return jdbcRepository.update(table, table_key, map, key);
    }

    public void alreadyExists(String table) throws Exception {
        if (metadataService.metadataMap().containsKey(table) || metadataService.selectqueryMap().containsKey(table)) {
            throw new Exception(" metadata or selectquery already exists! ");
        }
    }

    public String initTable(String table) throws Exception {
        if (metadataService.metadataMap().containsKey(table)) {
            Metadata metadata = metadataService.metadataMap().get(table);
            if (metadata.alias_table != null && !metadata.alias_table.trim().isEmpty()) {
                return metadata.alias_table;
            }
            return metadata.table_name;
        }
        return table;
    }

    public Map<String, Object> fetch(MultivaluedMap<String, String> queryParameters, String table, String uuid,
            String table_key) throws Exception {
        String select_fields = ParamUtils.select_fields(queryParameters);
        if (metadataService.metadataMap().containsKey(table)) {
            Metadata metadata = metadataService.metadataMap().get(table);
            if (metadata.alias_table != null && !metadata.alias_table.trim().isEmpty()) {
                table = metadata.alias_table;
                table_key = metadata.table_key;
            }
        }
        return jdbcRepository.fetch(select_fields, table, table_key, uuid);
    }

    @ActionEvent
    public boolean delete(String table, String uuid, String table_key) throws Exception {
        if (metadataService.metadataMap().containsKey(table)) {
            Metadata metadata = metadataService.metadataMap().get(table);
            if (metadata.fields == null || metadata.fields.isEmpty()) {
                metadata.fields = metadataService.fielddefinitions(metadata.table_name);
            }
            if (isPassivable(metadata.fields)) {
                String passivableField = getPassivableField(metadata.fields);
                if (passivableField != null) {
                    Map<String, Object> map = Map.of(passivableField, false);
                    jdbcRepository.update(table, table_key, map, uuid);
                    return true;
                }
            }
            if (metadata.alias_table != null && !metadata.alias_table.trim().isEmpty()) {
                table = metadata.alias_table;
                table_key = metadata.table_key;
            }
        }
        return jdbcRepository.delete(table, table_key, uuid);
    }

    public boolean deleteFieldDefinitionsByMetadataUuid(String uuid) throws Exception {
        List<Object> uuidList = new ArrayList<>();
        uuidList.add(uuid);
        return jdbcRepository.query(DELETE_FROM_FD, uuidList);
    }

    public void batch(String[] queries) throws Exception {
        jdbcRepository.batch(queries);
    }

    public Metadata createMetadataTable(String uuid) throws Exception {
        return metadataService.createTableFromMetadata(uuid);
    }

    public boolean query(String query, List<Object> values) throws Exception {
        return jdbcRepository.query(query, values);
    }

}
