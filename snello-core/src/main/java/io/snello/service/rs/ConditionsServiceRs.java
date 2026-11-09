package io.snello.service.rs;

import io.quarkus.arc.properties.IfBuildProperty;
import io.snello.management.SnelloPlane;

import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.api.service.AbstractServiceRs;
import io.snello.model.events.ConditionCreateUpdateEvent;
import io.snello.model.events.ConditionDeleteEvent;
import io.snello.service.ApiService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

import static io.snello.management.AppConstants.CONDITIONS;
import static io.snello.management.AppConstants.CONDITIONS_PATH;

@Path(CONDITIONS_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class ConditionsServiceRs extends AbstractServiceRs {


    @Inject
    Event<ConditionCreateUpdateEvent> eventCreateUpdatePublisher;

    @Inject
    Event<ConditionDeleteEvent> eventDeletePublisher;

    @Inject
    ConditionsServiceRs(ApiService apiService) {
        super(apiService, CONDITIONS, "");
    }

    public ConditionsServiceRs() {
    }

    @Override
    protected void prePersist(Map<String, Object> object) throws Exception {
        super.prePersist(object);
        defaultOptionalColumns(object);
    }

    @Override
    protected void preUpdate(Map<String, Object> object) throws Exception {
        defaultOptionalColumns(object);
    }

    /**
     * {@code metadata_multijoin_uuid} (multijoin conditions only) and {@code query_params} are NOT NULL in the schema
     * but optional in the admin editor, which leaves them out: saving a condition from the UI failed with
     * "null value in column metadata_multijoin_uuid".
     */
    private static void defaultOptionalColumns(Map<String, Object> object) {
        for (String column : new String[]{"metadata_multijoin_uuid", "query_params"}) {
            if (object.get(column) == null) {
                object.put(column, "");
            }
        }
    }

    @Override
    protected void postPersist(Map<String, Object> object) {
        eventCreateUpdatePublisher.fireAsync(new ConditionCreateUpdateEvent(object));
    }

    @Override
    protected void postUpdate(Map<String, Object> object) {
        eventCreateUpdatePublisher.fireAsync(new ConditionCreateUpdateEvent(object));
    }

    @Override
    protected void postDelete(String id) {
        eventDeletePublisher.fireAsync(new ConditionDeleteEvent(id));
    }
}
