package io.snello.service.rs;

import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.api.service.AbstractServiceRs;
import io.snello.model.events.ActionCreateUpdateEvent;
import io.snello.model.events.ActionDeleteEvent;
import io.snello.service.ApiService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

import static io.snello.management.AppConstants.ACTIONS;
import static io.snello.management.AppConstants.ACTIONS_PATH;

@Path(ACTIONS_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
public class ActionsServiceRs extends AbstractServiceRs {

    @Inject
    Event<ActionCreateUpdateEvent> eventCreateUpdatePublisher;

    @Inject
    Event<ActionDeleteEvent> eventDeletePublisher;

    @Inject
    ActionsServiceRs(ApiService apiService) {
        super(apiService, ACTIONS, "");
    }

    public ActionsServiceRs() {
    }

    @Override
    protected void postPersist(Map<String, Object> object) {
        eventCreateUpdatePublisher.fireAsync(new ActionCreateUpdateEvent(object));
    }

    @Override
    protected void postUpdate(Map<String, Object> object) {
        eventCreateUpdatePublisher.fireAsync(new ActionCreateUpdateEvent(object));
    }

    @Override
    protected void postDelete(String id) {
        eventDeletePublisher.fireAsync(new ActionDeleteEvent(id));
    }
}
