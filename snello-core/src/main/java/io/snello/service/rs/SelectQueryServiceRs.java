package io.snello.service.rs;

import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.api.service.AbstractServiceRs;
import io.snello.model.events.SelectQueryCreateUpdateEvent;
import io.snello.model.events.SelectQueryDeleteEvent;
import io.snello.service.ApiService;
import io.snello.util.MetadataUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

import static io.snello.management.AppConstants.*;

@Path(SELECT_QUERY_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
public class SelectQueryServiceRs extends AbstractServiceRs {

    @Inject
    public SelectQueryServiceRs(ApiService apiService) {
        super(apiService, SELECT_QUERY, "query_name asc");
    }

    public SelectQueryServiceRs() {
        super();
    }

    @Inject
    Event<SelectQueryCreateUpdateEvent> eventCreateUpdatePublisher;

  @Inject
    Event<SelectQueryDeleteEvent> eventDeletePublisher;


    @Override
    protected void prePersist(Map<String, Object> object) throws Exception {
        if (object.get(QUERY_NAME) == null) {
            throw new Exception(MSG_QUERY_NAME_IS_EMPTY);
        }
        if (MetadataUtils.isReserved(object.get(QUERY_NAME))) {
            throw new Exception(MSG_QUERY_NAME_IS_RESERVED);
        }
    }

    @Override
    protected void postPersist(Map<String, Object> object) {
        eventCreateUpdatePublisher.fireAsync(new SelectQueryCreateUpdateEvent(object));
    }

    @Override
    protected void preUpdate(Map<String, Object> object) throws Exception {
        if (object.get(QUERY_NAME) == null) {
            throw new Exception(MSG_QUERY_NAME_IS_EMPTY);
        }
        if (MetadataUtils.isReserved(object.get(QUERY_NAME))) {
            throw new Exception(MSG_QUERY_NAME_IS_RESERVED);
        }
    }

    @Override
    protected void postUpdate(Map<String, Object> object) {
        eventCreateUpdatePublisher.fireAsync(new SelectQueryCreateUpdateEvent(object));
    }

    @Override
    protected void postDelete(String id) {
        eventDeletePublisher.fireAsync(new SelectQueryDeleteEvent(id));
    }
}
