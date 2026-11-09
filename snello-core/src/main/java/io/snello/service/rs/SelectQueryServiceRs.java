package io.snello.service.rs;

import io.quarkus.arc.properties.IfBuildProperty;
import io.snello.management.SnelloPlane;

import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.api.service.AbstractServiceRs;
import io.snello.model.events.SelectQueryCreateUpdateEvent;
import io.snello.model.events.SelectQueryDeleteEvent;
import io.snello.service.ApiService;
import io.quarkus.logging.Log;
import io.snello.util.MapReads;
import io.snello.util.MetadataUtils;
import io.snello.util.SqlSafety;
import jakarta.ws.rs.BadRequestException;
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
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
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
        validateOwnerColumn(object);
    }

    /** A protected SelectQuery must name its owner column, and it must be a plain identifier (it is spliced into SQL). */
    private void validateOwnerColumn(Map<String, Object> object) throws Exception {
        boolean protectedQuery = MapReads.bool(object, "api_protected", false);
        String field = MapReads.str(object, "username_field");
        Log.infof("selectquery %s: api_protected=%s username_field=%s", object.get(QUERY_NAME), protectedQuery, field);
        if (field != null && !field.isBlank() && (field.contains(".") || SqlSafety.isUnsafeOwnerColumn(field))) {
            throw new BadRequestException("username_field must be a plain column name without an operator suffix"
                    + " (_ne, _in, _like, ...): " + field);
        }
        if (protectedQuery && (field == null || field.isBlank())) {
            throw new BadRequestException("api_protected select query requires username_field");
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
        validateOwnerColumn(object);
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
