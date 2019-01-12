package io.snello.cms.service;

import io.micronaut.discovery.event.ServiceStartedEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.scheduling.annotation.Async;
import io.snello.cms.management.AppConstants;
import io.snello.cms.model.Condition;
import io.snello.cms.model.FieldDefinition;
import io.snello.cms.model.Metadata;
import io.snello.cms.model.events.*;
import io.snello.cms.repository.JdbcRepository;
import io.snello.cms.util.FieldDefinitionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Singleton
public class MetadataService {

    Logger logger = LoggerFactory.getLogger(MetadataService.class);

    Map<String, Metadata> metadataMap;
    Map<String, List<FieldDefinition>> fielddefinitionsMap;
    Map<String, List<Condition>> conditionsMap;

    @Inject
    JdbcRepository jdbcRepository;

    public MetadataService() {

    }

    @EventListener
    @Async
    void createOrUpdateMetadata(MetadataCreateUpdateEvent metadataCreateUpdateEvent) {
        logger.info("new MetadataCreateUpdateEvent " + metadataCreateUpdateEvent.toString());
        metadataMap().put(metadataCreateUpdateEvent.metadata.table_name, metadataCreateUpdateEvent.metadata);
    }

    @EventListener
    @Async
    void deleteMetadata(MetadataDeleteEvent metadataDeleteEvent) {
        logger.info("new MetadataDeleteEvent " + metadataDeleteEvent.toString());
        for (Metadata metadata : metadataMap().values()) {
            if (metadata.uuid.equals(metadataDeleteEvent.uuid)) {
                metadataMap().remove(metadata.table_name);
                break;
            }
        }
    }

    @EventListener
    @Async
    void createOrUpdateCondition(ConditionCreateUpdateEvent conditionCreateUpdateEvent) {
        logger.info("new ConditionCreateUpdateEvent " + conditionCreateUpdateEvent.toString());
        List<Condition> conditions = null;
        if (conditionsMap().containsKey(conditionCreateUpdateEvent.condition.metadata_name)) {
            conditions = conditionsMap().get(conditionCreateUpdateEvent.condition.metadata_name);
        } else {
            conditions = new ArrayList<>();
        }
        if (!conditions.contains(conditionCreateUpdateEvent.condition)) {
            conditions.add(conditionCreateUpdateEvent.condition);
        }
        conditionsMap().put(conditionCreateUpdateEvent.condition.metadata_name, conditions);
    }

    @EventListener
    @Async
    void deleteCondition(ConditionDeleteEvent conditionDeleteEvent) {
        logger.info("new ConditionDeleteEvent " + conditionDeleteEvent.toString());
        for (List<Condition> conditions : conditionsMap().values()) {
            for (Condition condition : conditions) {
                if (condition.uuid.equals(conditionDeleteEvent.uuid)) {
                    conditions.remove(condition);
                    break;
                }
            }
        }
    }

    @EventListener
    @Async
    void createOrUpdateFieldDefinition(FieldDefinitionCreateUpdateEvent fieldDefinitionCreateUpdateEvent) {
        logger.info("new FieldDefinitionCreateUpdateEvent " + fieldDefinitionCreateUpdateEvent.toString());
        List<FieldDefinition> fieldDefinitions = null;
        if (fielddefinitionsMap().containsKey(fieldDefinitionCreateUpdateEvent.fieldDefinition.metadata_name)) {
            fieldDefinitions = fielddefinitionsMap().get(fieldDefinitionCreateUpdateEvent.fieldDefinition.metadata_name);
        } else {
            fieldDefinitions = new ArrayList<>();
        }
        if (!fieldDefinitions.contains(fieldDefinitionCreateUpdateEvent.fieldDefinition)) {
            fieldDefinitions.add(fieldDefinitionCreateUpdateEvent.fieldDefinition);
        }
        fielddefinitionsMap().put(fieldDefinitionCreateUpdateEvent.fieldDefinition.metadata_name, fieldDefinitions);
    }

    @EventListener
    @Async
    void deleteFieldDefinition(FieldDefinitionDeleteEvent fieldDefinitionDeleteEvent) {
        logger.info("new FieldDefinitionDeleteEvent " + fieldDefinitionDeleteEvent.toString());
        for (List<FieldDefinition> fieldDefinitions : fielddefinitionsMap().values()) {
            for (FieldDefinition fieldDefinition : fieldDefinitions) {
                if (fieldDefinition.uuid.equals(fieldDefinitionDeleteEvent.uuid)) {
                    fieldDefinitions.remove(fieldDefinition);
                    break;
                }
            }
        }
    }

    public boolean create(String uuid) throws Exception {
        Metadata metadata = null;
        for (Metadata metad : metadataMap.values()) {
            if (metad.uuid.equals(uuid)) {
                metadata = metad;
            }
        }
        if (metadata == null) {
            throw new Exception("metadata not existent!");
        }
        if (jdbcRepository.verifyTable(metadata.table_name)) {
            throw new Exception("table already existent!");
        }
        if (metadata.creation_query == null) {
            logger.info("no creation query foud in metedata object...i need to create...");
            List<FieldDefinition> fields = fielddefinitionsMap().get(metadata.table_name);
            if (fields == null || fields.size() == 0) {
                throw new Exception("metadata without fields: "+metadata.toString());
            }
            StringBuffer sb = new StringBuffer(" CREATE TABLE " + metadata.table_name + " (");
            if (metadata.table_key_type.equals("autoincrement")) {
                sb.append(metadata.table_key + " int NOT NULL AUTO_INCREMENT ");
            } else {
                sb.append(metadata.table_key + " VARCHAR(50) NOT NULL ");
            }
            for (FieldDefinition fieldDefinition : fields) {
                if (fieldDefinition.sql_definition != null && !fieldDefinition.sql_definition.trim().isEmpty()) {
                    sb.append(",").append(fieldDefinition.sql_definition);
                } else {
                    sb.append(",").append(FieldDefinitionUtils.sql(fieldDefinition));
                }
            }
            sb.append(", PRIMARY KEY (" + metadata.table_key + ")").append(")  ENGINE=INNODB;");
            logger.info("QUERY CREATION TABLE: " + sb.toString());
            jdbcRepository.batch(new String[]{sb.toString()});
        } else {
            logger.info("creation query foud in metedata object: " + metadata.creation_query);

            jdbcRepository.batch(new String[]{metadata.creation_query});
        }
        return true;
    }


    public Map<String, List<FieldDefinition>> fielddefinitionsMap() {
        if (this.fielddefinitionsMap == null) {
            this.fielddefinitionsMap = new HashMap<>();

            try {
                List<Map<String, Object>> liste = jdbcRepository.list(AppConstants.FIELD_DEFINITIONS, " metadata_name asc ");
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
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return this.fielddefinitionsMap;
    }

    public Map<String, Metadata> metadataMap() {
        if (this.metadataMap == null) {
            this.metadataMap = new HashMap<>();

            try {
                List<Map<String, Object>> liste = jdbcRepository.list(AppConstants.METADATAS, " table_name asc ");
                for (Map<String, Object> map : liste) {
                    Metadata metadata = new Metadata(map);
                    metadataMap.put(metadata.table_name, metadata);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return this.metadataMap;
    }

    public Map<String, List<Condition>> conditionsMap() {
        if (this.conditionsMap == null) {
            this.conditionsMap = new HashMap<>();

            try {
                List<Map<String, Object>> liste = jdbcRepository.list(AppConstants.CONDITIONS, " metadata_name asc ");
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
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return this.conditionsMap;
    }


    public Set<String> names() {
        return metadataMap().keySet();
    }

    public Metadata metadata(String table) {
        return metadataMap().get(table);
    }

    public List<FieldDefinition> fielddefinitions(String metadata_name) {
        return fielddefinitionsMap().get(metadata_name);
    }

    public List<Condition> conditions(String metadata_name) {
        return conditionsMap().get(metadata_name);
    }

    @EventListener
    @Async
    public void onLoad(final ServiceStartedEvent event) {
        logger.info("INIT METADATA SERVICE AT STARTUP: " + event.toString());
        metadataMap();
        fielddefinitionsMap();
        conditionsMap();
    }

}
