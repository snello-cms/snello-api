package io.snello.cms.service;

import io.micronaut.http.HttpParameters;
import io.snello.cms.model.Condition;
import io.snello.cms.model.Metadata;
import io.snello.cms.repository.JdbcRepository;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
public class ApiService {

    @Inject
    MetadataService metadataService;

    @Inject
    JdbcRepository jdbcRepository;

    public ApiService() {
    }


    public Metadata metadata(String metedata_name) {
        Metadata metadata = metadataService.metadataMap().get(metedata_name);
        return metadata;
    }


    public String table_key(String metedata_name) {
        Metadata metadata = metadataService.metadataMap().get(metedata_name);
        return metadata.table_key;
    }

    public long count(String table, HttpParameters httpParameters) throws Exception {
        String alias_condition = null;
        List<Condition> conditions = null;
        if (metadataService.metadataMap().containsKey(table)) {
            conditions = metadataService.conditionsMap().get(table);
            Metadata metadata = metadataService.metadataMap().get(table);
            if (metadata.alias_table != null && !metadata.alias_table.trim().isEmpty()) {
                table = metadata.alias_table;
                alias_condition = metadata.alias_condition;
            }
        }
        return jdbcRepository.count(table, alias_condition, httpParameters, conditions);
    }

    public boolean exist(String table, String table_key, Object uuid) throws Exception {
        return jdbcRepository.exist(table, table_key, uuid);
    }


    public List<Map<String, Object>> list(String table, HttpParameters httpParameters, String sort, int limit, int start) throws Exception {
        String select_fields = null;
        String alias_condition = null;
        List<Condition> conditions = null;
        if (metadataService.metadataMap().containsKey(table)) {
            Metadata metadata = metadataService.metadataMap().get(table);
            conditions = metadataService.conditionsMap().get(table);
            if (metadata.select_fields != null) {
                //"SELECT * FROM "
                select_fields = metadata.select_fields;
            }
            if (metadata.alias_table != null && !metadata.alias_table.trim().isEmpty()) {
                table = metadata.alias_table;
                alias_condition = metadata.alias_condition;
            }
        }
        return jdbcRepository.list(table, select_fields, alias_condition, httpParameters, conditions, sort, limit, start);
    }

    public Map<String, Object> create(String table, Map<String, Object> map, String table_key) throws Exception {
        init(table, table_key);
        return jdbcRepository.create(table, table_key, map);
    }

    public void init(String table, String table_key) {
        if (metadataService.metadataMap().containsKey(table)) {
            Metadata metadata = metadataService.metadataMap().get(table);
            if (metadata.alias_table != null && !metadata.alias_table.trim().isEmpty()) {
                table = metadata.alias_table;
            }
            table_key = metadata.table_key;
        }
    }

    public Map<String, Object> merge(String table, Map<String, Object> map, String key, String table_key) throws Exception {
        init(table, table_key);
        return jdbcRepository.update(table, table_key, map, key);
    }

    public Map<String, Object> fetch(String table, String uuid, String table_key) throws Exception {
        if (metadataService.metadataMap().containsKey(table)) {
            Metadata metadata = metadataService.metadataMap().get(table);
            if (metadata.alias_table != null && !metadata.alias_table.trim().isEmpty()) {
                table = metadata.alias_table;
                table_key = metadata.table_key;
            }
        }
        return jdbcRepository.fetch(table, table_key, uuid);
    }

    public boolean delete(String table, String uuid, String table_key) throws Exception {
        if (metadataService.metadataMap().containsKey(table)) {
            Metadata metadata = metadataService.metadataMap().get(table);
            table = metadata.alias_table;
            table_key = metadata.table_key;
        }
        return jdbcRepository.delete(table, table_key, uuid);
    }

    public void batch(String[] queries) throws Exception {
        jdbcRepository.batch(queries);
    }

    public boolean createMetadataTable(String uuid) throws Exception {
        return metadataService.create(uuid);
    }
}
