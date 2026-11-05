package io.snello.service.rs;

import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.api.service.AbstractServiceRs;
import io.snello.model.events.AiToolCreateUpdateEvent;
import io.snello.model.events.AiToolDeleteEvent;
import io.snello.service.ApiService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

import static io.snello.management.AppConstants.*;

@Path(AI_TOOLS_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
public class AiToolServiceRs extends AbstractServiceRs {

    @Inject
    public AiToolServiceRs(ApiService apiService) {
        super(apiService, AI_TOOLS, "name asc");
    }

    public AiToolServiceRs() {
        super();
    }

    @Inject
    Event<AiToolCreateUpdateEvent> eventCreateUpdatePublisher;

    @Inject
    Event<AiToolDeleteEvent> eventDeletePublisher;

    @Override
    protected void prePersist(Map<String, Object> object) throws Exception {
        if (object.get(NAME) == null) {
            throw new Exception(MSG_NAME_PARAM_IS_EMPTY);
        }
    }

    @Override
    protected void postPersist(Map<String, Object> object) {
        eventCreateUpdatePublisher.fireAsync(new AiToolCreateUpdateEvent(object));
    }

    @Override
    protected void preUpdate(Map<String, Object> object) throws Exception {
        if (object.get(NAME) == null) {
            throw new Exception(MSG_NAME_PARAM_IS_EMPTY);
        }
    }

    @Override
    protected void postUpdate(Map<String, Object> object) {
        eventCreateUpdatePublisher.fireAsync(new AiToolCreateUpdateEvent(object));
    }

    @Override
    protected void postDelete(String id) {
        eventDeletePublisher.fireAsync(new AiToolDeleteEvent(id));
    }
}
