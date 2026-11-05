package io.snello.service.rs;

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
