package io.snello.service.rs.ai;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.management.SnelloPlane;
import io.snello.service.ai.history.HistoryAccess;
import io.snello.service.ApiService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.snello.management.AppConstants.*;
import static jakarta.ws.rs.core.Response.ok;

@Path(AI_HISTORY_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class HistoryEntryServiceRs {

    @Inject
    ApiService apiService;

    @Inject
    HistoryAccess access;

    /** Own events; {@code ?scope=all} lifts the user filter for Admin (e.g. one session's transcript via {@code ?session_id=}). */
    @GET
    public Response list(
            @QueryParam(SORT_PARAM) String sort,
            @QueryParam(LIMIT_PARAM) String limit,
            @QueryParam(START_PARAM) String start,
            @Context UriInfo uriInfo) throws Exception {
        MultivaluedHashMap<String, String> params = new MultivaluedHashMap<>(uriInfo.getQueryParameters());
        access.applyListScope(HISTORY_EVENT, params);
        String effectiveSort = sort == null || sort.isBlank() ? "occurred_at desc" : sort;
        int l = Optional.ofNullable(limit).map(Integer::parseInt).orElse(20);
        int s = Optional.ofNullable(start).map(Integer::parseInt).orElse(0);
        List<Map<String, Object>> list = apiService.list(HISTORY_EVENT, params, effectiveSort, l, s);
        long count = apiService.count(HISTORY_EVENT, params);
        Log.debugf("[history] events list: session_id=%s sort=%s start=%d limit=%d -> %d rows of %d",
                params.getFirst("session_id"), effectiveSort, s, l, list == null ? 0 : list.size(), count);
        return ok(list)
                .header(SIZE_HEADER_PARAM, count)
                .header(TOTAL_COUNT_HEADER_PARAM, count)
                .build();
    }

    @GET
    @Path("/{id}")
    public Response fetch(@PathParam("id") String id) throws Exception {
        Map<String, Object> row = apiService.fetch(null, HISTORY_EVENT, id, UUID);
        if (row == null) {
            Log.debugf("[history] event %s not found", id);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (!access.canRead(HISTORY_EVENT, row)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        return ok(row).build();
    }
}
