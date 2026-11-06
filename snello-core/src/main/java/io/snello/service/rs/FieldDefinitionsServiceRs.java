package io.snello.service.rs;

import io.quarkus.arc.properties.IfBuildProperty;
import io.snello.management.SnelloPlane;


import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.api.service.AbstractServiceRs;
import io.snello.management.AppConstants;
import io.snello.model.events.FieldDefinitionCreateUpdateEvent;
import io.snello.model.events.FieldDefinitionDeleteEvent;
import io.quarkus.logging.Log;
import io.snello.service.AiProfileValidator;
import io.snello.service.ApiService;
import io.snello.service.TableSchemaService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

import static io.snello.management.AppConstants.FIELD_DEFINITIONS;
import static io.snello.management.AppConstants.FIELD_DEFINITIONS_PATH;

@Path(FIELD_DEFINITIONS_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class FieldDefinitionsServiceRs extends AbstractServiceRs {

    @Inject
    AiProfileValidator aiProfileValidator;

    @Inject
    Event<FieldDefinitionCreateUpdateEvent> eventUpdatePublisher;
    @Inject
    Event<FieldDefinitionDeleteEvent> eventDeletePublisher;
    @Inject
    TableSchemaService tableSchemaService;

    @Inject
    FieldDefinitionsServiceRs(ApiService apiService) {
        super(apiService, FIELD_DEFINITIONS, "name asc");
    }

    public FieldDefinitionsServiceRs() {
    }

    @Override
    protected void prePersist(Map<String, Object> object) throws Exception {
        object.put(AppConstants.UUID, java.util.UUID.randomUUID().toString());
        aiProfileValidator.prepareFieldRow(object);
        // table already created? then the new field needs its column
        tableSchemaService.onFieldCreated(object);
    }

    @Override
    protected void preUpdate(String id, Map<String, Object> object) throws Exception {
        aiProfileValidator.prepareFieldRow(object);
        Map<String, Object> stored = getApiService().fetch(null, FIELD_DEFINITIONS, id, AppConstants.UUID);
        if (stored == null || stored.isEmpty()) {
            Log.warnf("TABLE SYNC skipped: field definition %s not found", id);
            return;
        }
        // table already created? then a renamed field needs its column renamed
        tableSchemaService.onFieldUpdated(stored, object);
    }

    @Override
    protected void postPersist(Map<String, Object> object) {
        eventUpdatePublisher.fireAsync(new FieldDefinitionCreateUpdateEvent(object));
    }

    @Override
    protected void postUpdate(Map<String, Object> object) {
        eventUpdatePublisher.fireAsync(new FieldDefinitionCreateUpdateEvent(object));
    }

    @Override
    protected void postDelete(String id) {
        // by design the column is NOT dropped: deleting a field definition never removes data
        Log.infof("TABLE SYNC field definition %s deleted: its column (if any) is kept", id);
        eventDeletePublisher.fireAsync(new FieldDefinitionDeleteEvent(id));
    }

}
