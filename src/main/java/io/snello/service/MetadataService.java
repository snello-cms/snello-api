package io.snello.service;

import io.micronaut.context.event.ApplicationEventPublisher;
import io.snello.model.*;
import io.snello.model.events.ConditionCreateUpdateEvent;
import io.snello.repository.JdbcRepository;
import io.snello.util.ConditionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

import static io.snello.management.AppConstants.*;

@Singleton
public class MetadataService {

    Logger logger = LoggerFactory.getLogger(MetadataService.class);

    Map<String, Metadata> metadataMap;
    Map<String, SelectQuery> selectqueryMap;
    Map<String, Map<String, FieldDefinition>> fielddefinitionsMap;
    Map<String, List<Condition>> conditionsMap;
    Map<String, Draggable> draggablesMap;
    Map<String, Droppable> droppablesMap;

    @Inject
    ApplicationEventPublisher eventPublisher;

    @Inject
    JdbcRepository jdbcRepository;


    public MetadataService() {

    }

    public Metadata byUUid(String uuid) throws Exception {
        for (Metadata metadata : metadataMap.values()) {
            if (metadata.uuid.equals(uuid)) {
                return metadata;
            }
        }
        throw new Exception("metadata not existent!");
    }

    public Metadata createTableFromMetadataAndFields(Metadata metadata, List<FieldDefinition> fieldDefinitions) throws Exception {
        if (jdbcRepository.verifyTable(metadata.table_name)) {
            throw new Exception("table already existent!");
        }
        if (metadata.creation_query == null) {
            logger.info("no creation query found in metedata object...i need to createTableFromMetadata...");
            if (fieldDefinitions == null || fieldDefinitions.size() == 0) {
                throw new Exception("selectQuery without fields: " + metadata.toString());
            }
            List<String> relatedTables = new ArrayList<>();
            List<Condition> conditions = new ArrayList<>();
            String sqlQuery = jdbcRepository.createTableSql(metadata, fieldDefinitions, relatedTables, conditions);
            jdbcRepository.executeQuery(sqlQuery);
            for (String qq : relatedTables) {
                jdbcRepository.executeQuery(qq);
            }
            for (Condition cc : conditions) {
                jdbcRepository.create(CONDITIONS, UUID, cc.toMap());
                eventPublisher.publishEvent(new ConditionCreateUpdateEvent(cc.toMap()));
            }
        } else {
            logger.info("creation query foud in metedata object: " + metadata.creation_query);
            jdbcRepository.executeQuery(metadata.creation_query);
        }
        return metadata;
    }

    public Metadata createTableFromMetadata(String uuid) throws Exception {
        Metadata metadata = byUUid(uuid);
        Map<String, FieldDefinition> fields = fielddefinitionsMap().get(metadata.table_name);
        if (fields == null || fields.size() == 0) {
            throw new Exception("selectQuery without fields: " + metadata.toString());
        }
        return createTableFromMetadataAndFields(metadata, new ArrayList<>(fields.values()));
    }


    public Map<String, Map<String, FieldDefinition>> fielddefinitionsMap() throws Exception {
        if (this.fielddefinitionsMap == null) {
            this.fielddefinitionsMap = new TreeMap<>();

            List<Map<String, Object>> liste = jdbcRepository.list(FIELD_DEFINITIONS, " metadata_name asc ");
            if (liste != null) {
                for (Map<String, Object> map : liste) {
                    FieldDefinition fieldDefinition = new FieldDefinition(map);
                    if (fielddefinitionsMap.containsKey(fieldDefinition.metadata_name)) {
                        Map<String, FieldDefinition> fieldDefinitions = fielddefinitionsMap.get(fieldDefinition.metadata_name);
                        fieldDefinitions.put(fieldDefinition.uuid, fieldDefinition);
                        fielddefinitionsMap.put(fieldDefinition.metadata_name, fieldDefinitions);
                    } else {
                        Map<String, FieldDefinition> fieldDefinitions = new HashMap<>();
                        fieldDefinitions.put(fieldDefinition.uuid, fieldDefinition);
                        fielddefinitionsMap.put(fieldDefinition.metadata_name, fieldDefinitions);
                    }
                }
            }
        }
        return this.fielddefinitionsMap;
    }

    public Map<String, Draggable> draggablesMap() throws Exception {
        if (this.draggablesMap == null) {
            this.draggablesMap = new TreeMap<>();
            List<Map<String, Object>> liste = jdbcRepository.list(DRAGGABLES, " name asc ");
            if (liste != null) {
                for (Map<String, Object> map : liste) {
                    Draggable draggable = new Draggable(map);
                    draggablesMap.put(draggable.uuid, draggable);
                }
            }
        }
        return this.draggablesMap;
    }

    public Map<String, Droppable> droppablesMap() throws Exception {
        if (this.droppablesMap == null) {
            this.droppablesMap = new TreeMap<>();
            List<Map<String, Object>> liste = jdbcRepository.list(DROPPABLES, " name asc ");
            if (liste != null) {
                for (Map<String, Object> map : liste) {
                    Droppable droppable = new Droppable(map);
                    droppablesMap.put(droppable.uuid, droppable);
                }
            }
        }
        return this.droppablesMap;
    }

    public Map<String, Metadata> metadataMap() throws Exception {
        if (this.metadataMap == null) {
            this.metadataMap = new TreeMap<>();
            List<Map<String, Object>> liste = jdbcRepository.list(METADATAS, " table_name asc ");
            if (liste != null) {
                for (Map<String, Object> map : liste) {
                    Metadata metadata = new Metadata(map);
                    metadataMap.put(metadata.table_name, metadata);
                }
            }
        }
        return this.metadataMap;
    }

    public Map<String, SelectQuery> selectqueryMap() throws Exception {
        if (this.selectqueryMap == null) {
            this.selectqueryMap = new TreeMap<>();
            List<Map<String, Object>> liste = jdbcRepository.list(SELECT_QUERY, " query_name asc ");
            if (liste != null) {
                for (Map<String, Object> map : liste) {
                    SelectQuery selectQuery = new SelectQuery(map);
                    selectqueryMap.put(selectQuery.query_name, selectQuery);
                }
            }
        }
        return this.selectqueryMap;
    }

    public Map<String, List<Condition>> conditionsMap() throws Exception {
        if (this.conditionsMap == null) {
            this.conditionsMap = new TreeMap<>();
            List<Map<String, Object>> liste = jdbcRepository.list(CONDITIONS, " metadata_name asc ");
            if (liste != null) {
                for (Map<String, Object> map : liste) {
                    Condition condition = new Condition(map);
                    if (conditionsMap.containsKey(condition.metadata_name)) {
                        List<Condition> conditionList = conditionsMap.get(condition.metadata_name);
                        conditionList.add(condition);
                        conditionsMap.put(condition.metadata_name, conditionList);
                    } else {
                        List<Condition> conditionList = new ArrayList<>();
                        conditionList.add(condition);
                        conditionsMap.put(condition.metadata_name, conditionList);
                    }
                }
            }
        }
        return this.conditionsMap;
    }


    public Set<String> names() throws Exception {
        return metadataMap().keySet();
    }

    public Metadata metadata(String table) throws Exception {
        return metadataMap().get(table);
    }

    public List<FieldDefinition> fielddefinitions(String metadata_name) throws Exception {
        return new ArrayList<>(fielddefinitionsMap().get(metadata_name).values());
    }

    public List<Condition> conditions(String metadata_name) throws Exception {
        return conditionsMap().get(metadata_name);
    }

    public String initTableKey(String table, String table_key) throws Exception {
        if (metadataMap().containsKey(table)) {
            Metadata metadata = metadataMap().get(table);
            return metadata.table_key;
        }
        return table_key;
    }


}
