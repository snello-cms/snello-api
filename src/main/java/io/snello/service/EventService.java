package io.snello.service;

import io.micronaut.discovery.event.ServiceStartedEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.scheduling.annotation.Async;
import io.snello.model.Condition;
import io.snello.model.Draggable;
import io.snello.model.Droppable;
import io.snello.model.SelectQuery;
import io.snello.model.events.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class EventService {

    Logger logger = LoggerFactory.getLogger(EventService.class);

    @Inject
    MetadataService metadataService;

    @EventListener
    @Async
    public void onLoad(final ServiceStartedEvent event) {
        logger.error("START METADATA SERVICE AT STARTUP: " + event.toString());
        try {
            resetAll();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        logger.error("END METADATA SERVICE AT STARTUP: " + event.toString());
    }

    public void resetAll() throws Exception {
        // null to maps
        metadataService.conditionsMap = null;
        metadataService.selectqueryMap = null;
        // reset
        metadataService.conditionsMap();
        metadataService.selectqueryMap();
    }

    public void resetMetadata() throws Exception {
        metadataService.metadataMap = null;
        metadataService.fielddefinitionsMap = null;
        metadataService.metadataMap();
        metadataService.fielddefinitionsMap();
    }

    @EventListener
    @Async
    void createOrUpdateMetadata(MetadataCreateUpdateEvent metadataCreateUpdateEvent) {
        logger.info("new MetadataCreateUpdateEvent " + metadataCreateUpdateEvent.toString());
        try {
            resetMetadata();
//            metadataService.metadataMap().put(metadataCreateUpdateEvent.metadata.table_name, metadataCreateUpdateEvent.metadata);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @EventListener
    @Async
    void deleteMetadata(MetadataDeleteEvent metadataDeleteEvent) {
        logger.info("new MetadataDeleteEvent " + metadataDeleteEvent.toString());
        try {
            resetMetadata();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @EventListener
    @Async
    void createOrUpdateFieldDefinition(FieldDefinitionCreateUpdateEvent fieldDefinitionCreateUpdateEvent) {
        logger.info("new FieldDefinitionCreateUpdateEvent " + fieldDefinitionCreateUpdateEvent.toString());
//        Map<String, FieldDefinition> fieldDefinitions = null;
//        try {
//            if (metadataService.fielddefinitionsMap().containsKey(fieldDefinitionCreateUpdateEvent.fieldDefinition.metadata_name)) {
//                fieldDefinitions = metadataService.fielddefinitionsMap().get(fieldDefinitionCreateUpdateEvent.fieldDefinition.metadata_name);
//            } else {
//                fieldDefinitions = new HashMap<>();
//            }
//        } catch (Exception e) {
//            logger.error(e.getMessage(), e);
//        }
////        if (!fieldDefinitions.containsKey(fieldDefinitionCreateUpdateEvent.fieldDefinition.uuid)) {
////            fieldDefinitions.put(fieldDefinitionCreateUpdateEvent.fieldDefinition.uuid, fieldDefinitionCreateUpdateEvent.fieldDefinition);
////        }
//        fieldDefinitions.put(fieldDefinitionCreateUpdateEvent.fieldDefinition.uuid, fieldDefinitionCreateUpdateEvent.fieldDefinition);
//        try {
//            metadataService.fielddefinitionsMap().put(fieldDefinitionCreateUpdateEvent.fieldDefinition.metadata_name, fieldDefinitions);
//        } catch (Exception e) {
//            logger.error(e.getMessage(), e);
//        }
        try {
            resetMetadata();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @EventListener
    @Async
    void deleteFieldDefinition(FieldDefinitionDeleteEvent fieldDefinitionDeleteEvent) {
        logger.info("new FieldDefinitionDeleteEvent " + fieldDefinitionDeleteEvent.toString());
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
//            logger.error(e.getMessage(), e);
//        }
        try {
            resetMetadata();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @EventListener
    @Async
    void createOrUpdateDraggable(DraggableCreateUpdateEvent draggableCreateUpdateEvent) {
        logger.info("new DraggableCreateUpdateEvent " + draggableCreateUpdateEvent.toString());
        try {
            metadataService.draggablesMap().put(draggableCreateUpdateEvent.draggable.uuid, draggableCreateUpdateEvent.draggable);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @EventListener
    @Async
    void deleteDraggable(DraggableDeleteEvent draggableDeleteEvent) {
        logger.info("new DraggableDeleteEvent " + draggableDeleteEvent.toString());
        try {
            for (Draggable draggable : metadataService.draggablesMap().values()) {
                if (draggable.uuid.equals(draggableDeleteEvent.uuid)) {
                    metadataService.draggablesMap().remove(draggable.uuid);
                    break;
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @EventListener
    @Async
    void createOrUpdateDroppable(DroppableCreateUpdateEvent droppableCreateUpdateEvent) {
        logger.info("new DroppableCreateUpdateEvent " + droppableCreateUpdateEvent.toString());
        try {
            metadataService.droppablesMap().put(droppableCreateUpdateEvent.droppable.uuid, droppableCreateUpdateEvent.droppable);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @EventListener
    @Async
    void deleteDroppable(DroppableDeleteEvent droppableDeleteEvent) {
        logger.info("new DroppableDeleteEvent " + droppableDeleteEvent.toString());
        try {
            for (Droppable droppable : metadataService.droppablesMap().values()) {
                if (droppable.uuid.equals(droppableDeleteEvent.uuid)) {
                    metadataService.droppablesMap().remove(droppable.uuid);
                    break;
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @EventListener
    @Async
    void createOrUpdateSelectQuery(SelectQueryCreateUpdateEvent selectQueryCreateUpdateEvent) {
        logger.info("new SelectQueryCreateUpdateEvent " + selectQueryCreateUpdateEvent.toString());
        try {
            metadataService.selectqueryMap().put(selectQueryCreateUpdateEvent.selectQuery.query_name, selectQueryCreateUpdateEvent.selectQuery);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @EventListener
    @Async
    void deleteSelectQuery(SelectQueryDeleteEvent selectQueryDeleteEvent) {
        logger.info("new SelectQueryDeleteEvent " + selectQueryDeleteEvent.toString());
        try {
            for (SelectQuery selectQuery : metadataService.selectqueryMap().values()) {
                if (selectQuery.uuid.equals(selectQueryDeleteEvent.uuid)) {
                    metadataService.selectqueryMap().remove(selectQuery.query_name);
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
            if (metadataService.conditionsMap().containsKey(conditionCreateUpdateEvent.condition.metadata_name)) {
                conditions = metadataService.conditionsMap().get(conditionCreateUpdateEvent.condition.metadata_name);
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
            metadataService.conditionsMap().put(conditionCreateUpdateEvent.condition.metadata_name, conditions);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @EventListener
    @Async
    void deleteCondition(ConditionDeleteEvent conditionDeleteEvent) {
        logger.info("new ConditionDeleteEvent " + conditionDeleteEvent.toString());
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
            logger.error(e.getMessage(), e);
        }
    }


}
