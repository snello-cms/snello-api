package io.snello.cms.service;

import io.micronaut.discovery.event.ServiceStartedEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.scheduling.annotation.Async;
import io.snello.cms.model.Condition;
import io.snello.cms.model.FieldDefinition;
import io.snello.cms.model.Metadata;
import io.snello.cms.model.SelectQuery;
import io.snello.cms.model.events.*;
import io.snello.cms.repository.JdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

import static io.snello.cms.management.AppConstants.*;

@Singleton
public class MetadataService {

    Logger logger = LoggerFactory.getLogger(MetadataService.class);

    Map<String, Metadata> metadataMap;
    Map<String, SelectQuery> selectqueryMap;
    Map<String, List<FieldDefinition>> fielddefinitionsMap;
    Map<String, List<Condition>> conditionsMap;

    @Inject
    JdbcRepository jdbcRepository;

    public MetadataService() {

    }

    @EventListener
    @Async
    void createOrUpdateSelectQuery(SelectQueryCreateUpdateEvent selectQueryCreateUpdateEvent) {
        logger.info("new SelectQueryCreateUpdateEvent " + selectQueryCreateUpdateEvent.toString());
        try {
            selectqueryMap().put(selectQueryCreateUpdateEvent.selectQuery.query_name, selectQueryCreateUpdateEvent.selectQuery);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @EventListener
    @Async
    void deleteSelectQuery(SelectQueryDeleteEvent selectQueryDeleteEvent) {
        logger.info("new SelectQueryDeleteEvent " + selectQueryDeleteEvent.toString());
        try {
            for (SelectQuery selectQuery : selectqueryMap().values()) {
                if (selectQuery.uuid.equals(selectQueryDeleteEvent.uuid)) {
                    selectqueryMap().remove(selectQuery.query_name);
                    break;
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }


    @EventListener
    @Async
    void createOrUpdateMetadata(MetadataCreateUpdateEvent metadataCreateUpdateEvent) {
        logger.info("new MetadataCreateUpdateEvent " + metadataCreateUpdateEvent.toString());
        try {
            metadataMap().put(metadataCreateUpdateEvent.metadata.table_name, metadataCreateUpdateEvent.metadata);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @EventListener
    @Async
    void deleteMetadata(MetadataDeleteEvent metadataDeleteEvent) {
        logger.info("new MetadataDeleteEvent " + metadataDeleteEvent.toString());
        try {
            for (Metadata metadata : metadataMap().values()) {
                if (metadata.uuid.equals(metadataDeleteEvent.uuid)) {
                    metadataMap().remove(metadata.table_name);
                    break;
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @EventListener
    @Async
    void createOrUpdateCondition(ConditionCreateUpdateEvent conditionCreateUpdateEvent) {
        logger.info("new ConditionCreateUpdateEvent " + conditionCreateUpdateEvent.toString());
        List<Condition> conditions = null;
        try {
            if (conditionsMap().containsKey(conditionCreateUpdateEvent.condition.metadata_name)) {
                conditions = conditionsMap().get(conditionCreateUpdateEvent.condition.metadata_name);
            } else {
                conditions = new ArrayList<>();
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        if (!conditions.contains(conditionCreateUpdateEvent.condition)) {
            conditions.add(conditionCreateUpdateEvent.condition);
        }
        try {
            conditionsMap().put(conditionCreateUpdateEvent.condition.metadata_name, conditions);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @EventListener
    @Async
    void deleteCondition(ConditionDeleteEvent conditionDeleteEvent) {
        logger.info("new ConditionDeleteEvent " + conditionDeleteEvent.toString());
        try {
            for (List<Condition> conditions : conditionsMap().values()) {
                for (Condition condition : conditions) {
                    if (condition.uuid.equals(conditionDeleteEvent.uuid)) {
                        conditions.remove(condition);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @EventListener
    @Async
    void createOrUpdateFieldDefinition(FieldDefinitionCreateUpdateEvent fieldDefinitionCreateUpdateEvent) {
        logger.info("new FieldDefinitionCreateUpdateEvent " + fieldDefinitionCreateUpdateEvent.toString());
        List<FieldDefinition> fieldDefinitions = null;
        try {
            if (fielddefinitionsMap().containsKey(fieldDefinitionCreateUpdateEvent.fieldDefinition.metadata_name)) {
                fieldDefinitions = fielddefinitionsMap().get(fieldDefinitionCreateUpdateEvent.fieldDefinition.metadata_name);
            } else {
                fieldDefinitions = new ArrayList<>();
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        if (!fieldDefinitions.contains(fieldDefinitionCreateUpdateEvent.fieldDefinition)) {
            fieldDefinitions.add(fieldDefinitionCreateUpdateEvent.fieldDefinition);
        }
        try {
            fielddefinitionsMap().put(fieldDefinitionCreateUpdateEvent.fieldDefinition.metadata_name, fieldDefinitions);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @EventListener
    @Async
    void deleteFieldDefinition(FieldDefinitionDeleteEvent fieldDefinitionDeleteEvent) {
        logger.info("new FieldDefinitionDeleteEvent " + fieldDefinitionDeleteEvent.toString());
        try {
            for (List<FieldDefinition> fieldDefinitions : fielddefinitionsMap().values()) {
                for (FieldDefinition fieldDefinition : fieldDefinitions) {
                    if (fieldDefinition.uuid.equals(fieldDefinitionDeleteEvent.uuid)) {
                        fieldDefinitions.remove(fieldDefinition);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    public Metadata byUUid(String uuid) throws Exception {
        for (Metadata metadata : metadataMap.values()) {
            if (metadata.uuid.equals(uuid)) {
                return metadata;
            }
        }
        throw new Exception("metadata not existent!");
    }

    public Metadata createTableFromMetadata(String uuid) throws Exception {
        Metadata metadata = byUUid(uuid);
        if (jdbcRepository.verifyTable(metadata.table_name)) {
            throw new Exception("table already existent!");
        }
        if (metadata.creation_query == null) {
            logger.info("no creation query found in metedata object...i need to createTableFromMetadata...");
            List<FieldDefinition> fields = fielddefinitionsMap().get(metadata.table_name);
            if (fields == null || fields.size() == 0) {
                throw new Exception("selectQuery without fields: " + metadata.toString());
            }
            String sqlQuery = jdbcRepository.createTableSql(metadata, fields);
            jdbcRepository.batch(new String[]{sqlQuery});
        } else {
            logger.info("creation query foud in metedata object: " + metadata.creation_query);
            jdbcRepository.batch(new String[]{metadata.creation_query});
        }
        return metadata;
    }


    public Map<String, List<FieldDefinition>> fielddefinitionsMap() throws Exception {
        if (this.fielddefinitionsMap == null) {
            this.fielddefinitionsMap = new TreeMap<>();

            List<Map<String, Object>> liste = jdbcRepository.list(FIELD_DEFINITIONS, " metadata_name asc ");
            for (Map<String, Object> map : liste) {
                FieldDefinition fieldDefinition = new FieldDefinition(map);
                if (fielddefinitionsMap.containsKey(fieldDefinition.metadata_name)) {
                    List<FieldDefinition> fieldDefinitions = fielddefinitionsMap.get(fieldDefinition.metadata_name);
                    fieldDefinitions.add(fieldDefinition);
                    fielddefinitionsMap.put(fieldDefinition.metadata_name, fieldDefinitions);
                } else {
                    List<FieldDefinition> fieldDefinitions = new ArrayList<>();
                    fieldDefinitions.add(fieldDefinition);
                    fielddefinitionsMap.put(fieldDefinition.metadata_name, fieldDefinitions);
                }
            }
        }
        return this.fielddefinitionsMap;
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
        return fielddefinitionsMap().get(metadata_name);
    }

    public List<Condition> conditions(String metadata_name) throws Exception {
        return conditionsMap().get(metadata_name);
    }

    @EventListener
    @Async
    public void onLoad(final ServiceStartedEvent event) {
        logger.info("START METADATA SERVICE AT STARTUP: " + event.toString());
        try {
            metadataMap();
            fielddefinitionsMap();
            conditionsMap();
            selectqueryMap();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        logger.info("END METADATA SERVICE AT STARTUP: " + event.toString());
    }

}
