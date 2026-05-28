package io.snello.service;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.snello.model.Action;
import io.snello.model.AiTool;
import io.snello.model.Condition;
import io.snello.model.SelectQuery;
import io.snello.model.events.*;
import io.snello.util.ActionUtils;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class EventService {
    
    @Inject
    MetadataService metadataService;

    public void onLoad(@Observes StartupEvent event) {
        Log.info("******** START METADATA SERVICE AT STARTUP ******** ");
        try {
            resetAll();
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
        Log.info("******** END METADATA SERVICE AT STARTUP ********");
    }

    public void resetAll() throws Exception {
        // null to maps
        metadataService.conditionsMap = null;
        metadataService.selectqueryMap = null;
        metadataService.aiToolsMap = null;
        metadataService.actionsMap = null;
        // reset
        metadataService.conditionsMap();
        metadataService.selectqueryMap();
        metadataService.aiToolsMap();
        metadataService.actionsMap();
    }

    public void resetMetadata() throws Exception {
        metadataService.metadataMap = null;
        metadataService.fielddefinitionsMap = null;
        metadataService.metadataMap();
        metadataService.fielddefinitionsMap();
    }

    void createOrUpdateMetadata(@ObservesAsync MetadataCreateUpdateEvent metadataCreateUpdateEvent) {
        Log.info("new MetadataCreateUpdateEvent " + metadataCreateUpdateEvent.toString());
        try {
            resetMetadata();
//            metadataService.metadataMap().put(metadataCreateUpdateEvent.metadata.table_name, metadataCreateUpdateEvent.metadata);
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }

    void deleteMetadata(@ObservesAsync MetadataDeleteEvent metadataDeleteEvent) {
        Log.info("new MetadataDeleteEvent " + metadataDeleteEvent.toString());
        try {
            resetMetadata();
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }


    void createOrUpdateFieldDefinition(@ObservesAsync FieldDefinitionCreateUpdateEvent fieldDefinitionCreateUpdateEvent) {
        Log.info("new FieldDefinitionCreateUpdateEvent " + fieldDefinitionCreateUpdateEvent.toString());
//        Map<String, FieldDefinition> fieldDefinitions = null;
//        try {
//            if (metadataService.fielddefinitionsMap().containsKey(fieldDefinitionCreateUpdateEvent.fieldDefinition.metadata_name)) {
//                fieldDefinitions = metadataService.fielddefinitionsMap().get(fieldDefinitionCreateUpdateEvent.fieldDefinition.metadata_name);
//            } else {
//                fieldDefinitions = new HashMap<>();
//            }
//        } catch (Exception e) {
//            Log.error(e.getMessage(), e);
//        }
////        if (!fieldDefinitions.containsKey(fieldDefinitionCreateUpdateEvent.fieldDefinition.uuid)) {
////            fieldDefinitions.put(fieldDefinitionCreateUpdateEvent.fieldDefinition.uuid, fieldDefinitionCreateUpdateEvent.fieldDefinition);
////        }
//        fieldDefinitions.put(fieldDefinitionCreateUpdateEvent.fieldDefinition.uuid, fieldDefinitionCreateUpdateEvent.fieldDefinition);
//        try {
//            metadataService.fielddefinitionsMap().put(fieldDefinitionCreateUpdateEvent.fieldDefinition.metadata_name, fieldDefinitions);
//        } catch (Exception e) {
//            Log.error(e.getMessage(), e);
//        }
        try {
            resetMetadata();
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }


    void deleteFieldDefinition(@ObservesAsync FieldDefinitionDeleteEvent fieldDefinitionDeleteEvent) {
        Log.info("new FieldDefinitionDeleteEvent " + fieldDefinitionDeleteEvent.toString());
//        try {
//            for (Map<String, FieldDefinition> fieldDefinitions : metadataService.fielddefinitionsMap().values()) {
//                for (FieldDefinition fieldDefinition : fieldDefinitions.values()) {
//                    if (fieldDefinition.uuid.equals(fieldDefinitionDeleteEvent.uuid)) {
//                        fieldDefinitions.remove(fieldDefinition.uuid);
//                        metadataService.fielddefinitionsMap().put(fieldDefinition.metadata_name, fieldDefinitions);
//                        break;
//                    }
//                }
//            }
//        } catch (Exception e) {
//            Log.error(e.getMessage(), e);
//        }
        try {
            resetMetadata();
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }



    void createOrUpdateSelectQuery(@ObservesAsync SelectQueryCreateUpdateEvent selectQueryCreateUpdateEvent) {
        Log.info("new SelectQueryCreateUpdateEvent " + selectQueryCreateUpdateEvent.toString());
        try {
            metadataService.selectqueryMap().put(selectQueryCreateUpdateEvent.selectQuery.query_name, selectQueryCreateUpdateEvent.selectQuery);
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }

    void deleteSelectQuery(@ObservesAsync SelectQueryDeleteEvent selectQueryDeleteEvent) {
        Log.info("new SelectQueryDeleteEvent " + selectQueryDeleteEvent.toString());
        try {
            for (SelectQuery selectQuery : metadataService.selectqueryMap().values()) {
                if (selectQuery.uuid.equals(selectQueryDeleteEvent.uuid)) {
                    metadataService.selectqueryMap().remove(selectQuery.query_name);
                    break;
                }
            }
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }

    void createOrUpdateAiTool(@ObservesAsync AiToolCreateUpdateEvent aiToolCreateUpdateEvent) {
        Log.info("new AiToolCreateUpdateEvent " + aiToolCreateUpdateEvent.toString());
        try {
            metadataService.aiToolsMap().put(aiToolCreateUpdateEvent.aiTool.name, aiToolCreateUpdateEvent.aiTool);
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }

    void deleteAiTool(@ObservesAsync AiToolDeleteEvent aiToolDeleteEvent) {
        Log.info("new AiToolDeleteEvent " + aiToolDeleteEvent.toString());
        try {
            for (AiTool aiTool : metadataService.aiToolsMap().values()) {
                if (aiTool.uuid.equals(aiToolDeleteEvent.uuid)) {
                    metadataService.aiToolsMap().remove(aiTool.name);
                    break;
                }
            }
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }

    void createOrUpdateAction(@ObservesAsync ActionCreateUpdateEvent actionCreateUpdateEvent) {
        Log.info("new ActionCreateUpdateEvent " + actionCreateUpdateEvent.toString());
        try {
            Action action = actionCreateUpdateEvent.action;
            metadataService.actionsMap().put(ActionUtils.actionKey(action.metadata_name, action.condition), action);
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }

    void deleteAction(@ObservesAsync ActionDeleteEvent actionDeleteEvent) {
        Log.info("new ActionDeleteEvent " + actionDeleteEvent.toString());
        try {
            for (Action action : metadataService.actionsMap().values()) {
                if (action.uuid.equals(actionDeleteEvent.uuid)) {
                    metadataService.actionsMap().remove(ActionUtils.actionKey(action.metadata_name, action.condition));
                    break;
                }
            }
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }


    void createOrUpdateCondition(@ObservesAsync ConditionCreateUpdateEvent conditionCreateUpdateEvent) {
        Log.info("new ConditionCreateUpdateEvent " + conditionCreateUpdateEvent.toString());
        List<Condition> conditions = new ArrayList<>();
        try {
            if (metadataService.conditionsMap().containsKey(conditionCreateUpdateEvent.condition.metadata_name)) {
                conditions = metadataService.conditionsMap().get(conditionCreateUpdateEvent.condition.metadata_name);
            }
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
        if (conditions == null) {
            conditions = new ArrayList<>();
        }
        if (!conditions.contains(conditionCreateUpdateEvent.condition)) {
            conditions.add(conditionCreateUpdateEvent.condition);
        }
        try {
            metadataService.conditionsMap().put(conditionCreateUpdateEvent.condition.metadata_name, conditions);
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }


    void deleteCondition(@ObservesAsync ConditionDeleteEvent conditionDeleteEvent) {
        Log.info("new ConditionDeleteEvent " + conditionDeleteEvent.toString());
        try {
            for (List<Condition> conditions : metadataService.conditionsMap().values()) {
                for (Condition condition : conditions) {
                    if (condition.uuid.equals(conditionDeleteEvent.uuid)) {
                        conditions.remove(condition);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }


}
