package io.snello.service;

import io.quarkus.logging.Log;
import io.snello.api.service.JdbcRepository;
import io.snello.model.*;
import io.snello.util.ActionUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;

import static io.snello.management.AppConstants.*;

@Singleton
public class MetadataService {
    
    Map<String, Metadata> metadataMap;
    Map<String, SelectQuery> selectqueryMap;
    Map<String, AiTool> aiToolsMap;
    Map<String, Action> actionsMap;
    Map<String, Map<String, FieldDefinition>> fielddefinitionsMap;
    Map<String, List<Condition>> conditionsMap;

    @Inject
    JdbcRepository jdbcRepository;


    public MetadataService() {

    }

    public Metadata byUUid(String uuid) throws Exception {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        for (Metadata metadata : metadataMap().values()) {
            if (metadata != null && uuid.equals(metadata.uuid)) {
                return metadata;
            }
        }
        throw new Exception("metadata not existent!");
    }

    public Metadata createTableFromMetadataAndFields(Metadata metadata, List<FieldDefinition> fieldDefinitions) throws Exception {
        if (metadata == null) {
            throw new Exception("metadata is null");
        }
        if (jdbcRepository.verifyTable(metadata.table_name)) {
            throw new Exception("table already existent!");
        }
        if (metadata.creation_query == null) {
            Log.info("no creation query found in metedata object...i need to createTableFromMetadata...");
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
//                eventPublisher.fireAsync(new ConditionCreateUpdateEvent(cc.toMap()));
            }
        } else {
            Log.info("creation query foud in metedata object: " + metadata.creation_query);
            jdbcRepository.executeQuery(metadata.creation_query);
        }
        return metadata;
    }

    public Metadata createTableFromMetadata(String uuid) throws Exception {
        Metadata metadata = byUUid(uuid);
        Map<String, FieldDefinition> fields = fielddefinitionsMap().get(metadata.uuid);
        if (fields == null || fields.size() == 0) {
            throw new Exception("selectQuery without fields: " + metadata.toString());
        }
        return createTableFromMetadataAndFields(metadata, new ArrayList<>(fields.values()));
    }


    public Map<String, Map<String, FieldDefinition>> fielddefinitionsMap() throws Exception {
        if (this.fielddefinitionsMap == null) {
            this.fielddefinitionsMap = new TreeMap<>();

            List<Map<String, Object>> liste = jdbcRepository.list(FIELD_DEFINITIONS, " metadata_name asc, view_index asc ");
            if (liste != null) {
                for (Map<String, Object> map : liste) {
                    FieldDefinition fieldDefinition = new FieldDefinition(map);
                    if (fielddefinitionsMap.containsKey(fieldDefinition.metadata_uuid)) {
                        Map<String, FieldDefinition> fieldDefinitions = fielddefinitionsMap.get(fieldDefinition.metadata_uuid);
                        fieldDefinitions.put(fieldDefinition.uuid, fieldDefinition);
                        fielddefinitionsMap.put(fieldDefinition.metadata_uuid, fieldDefinitions);
                    } else {
                        Map<String, FieldDefinition> fieldDefinitions = new HashMap<>();
                        fieldDefinitions.put(fieldDefinition.uuid, fieldDefinition);
                        fielddefinitionsMap.put(fieldDefinition.metadata_uuid, fieldDefinitions);
                    }
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

    public Map<String, AiTool> aiToolsMap() throws Exception {
        if (this.aiToolsMap == null) {
            this.aiToolsMap = new TreeMap<>();
            List<Map<String, Object>> liste = jdbcRepository.list(AI_TOOLS, " name asc ");
            if (liste != null) {
                for (Map<String, Object> map : liste) {
                    AiTool aiTool = new AiTool(map);
                    aiToolsMap.put(aiTool.name, aiTool);
                }
            }
        }
        return this.aiToolsMap;
    }

    public Map<String, Action> actionsMap() throws Exception {
        if (this.actionsMap == null) {
            this.actionsMap = new TreeMap<>();
            List<Map<String, Object>> liste = jdbcRepository.list(ACTIONS, " metadata_name asc, condition asc ");
            if (liste != null) {
                for (Map<String, Object> map : liste) {
                    Action action = new Action(map);
                    actionsMap.put(ActionUtils.actionKey(action.metadata_name, action.condition), action);
                }
            }
        }
        return this.actionsMap;
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
        Metadata metadata = metadataMap().get(metadata_name);
        if (metadata == null) {
            return new ArrayList<>();
        }
        Map<String, FieldDefinition> defs = fielddefinitionsMap().get(metadata.uuid);
        if (defs == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(defs.values());
    }

    public List<Condition> conditions(String metadata_name) throws Exception {
        List<Condition> conds = conditionsMap().get(metadata_name);
        return conds == null ? new ArrayList<>() : conds;
    }

    public String initTableKey(String table, String table_key) throws Exception {
        if (metadataMap().containsKey(table)) {
            Metadata metadata = metadataMap().get(table);
            return metadata.table_key;
        }
        return table_key;
    }


}
