package io.snello.service.rs;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.management.SnelloPlane;
import io.snello.model.pojo.IntrospectBatchRequest;
import io.snello.model.pojo.IntrospectCatalog;
import io.snello.model.pojo.IntrospectJob;
import io.snello.model.pojo.IntrospectJobLog;
import io.snello.model.pojo.IntrospectRequest;
import io.snello.model.pojo.IntrospectResult;
import io.snello.service.IntrospectBatchService;
import io.snello.service.IntrospectService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

import static io.snello.management.AppConstants.INTROSPECT_PATH;

/**
 * Reverse schema authoring: physical tables → metadata + fielddefinitions ({@code already_exist=true}).
 * <ul>
 *     <li>{@code GET /api/introspect/tables}: cheap catalog (counts + names)</li>
 *     <li>{@code POST /api/introspect/preview}: dry run; always 200 with {@link IntrospectResult}</li>
 *     <li>{@code POST /api/introspect/apply}: persist rows; 400 when invalid. Never runs DDL.</li>
 *     <li>{@code POST /api/introspect/batch}: enqueue auto-apply over remaining tables (202)</li>
 * </ul>
 */
@Path(INTROSPECT_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class IntrospectServiceRs {

    @Inject
    IntrospectService introspectService;

    @Inject
    IntrospectBatchService batchService;

    @Inject
    SecurityIdentity identity;

    @GET
    @Path("/tables")
    @RolesAllowed({"Admin", "admin", "Manager", "manager"})
    public IntrospectCatalog tables(@QueryParam("q") String q) throws Exception {
        Log.infof("GET introspect/tables q=%s", q);
        return introspectService.catalog(q);
    }

    @POST
    @Path("/preview")
    @RolesAllowed({"Admin", "admin", "Manager", "manager"})
    public IntrospectResult preview(IntrospectRequest request) throws Exception {
        Log.infof("POST introspect/preview tables=%s", request == null ? null : request.tables());
        return introspectService.preview(request);
    }

    @POST
    @Path("/apply")
    @RolesAllowed({"Admin", "admin", "Manager", "manager"})
    public Response apply(IntrospectRequest request) throws Exception {
        Log.infof("POST introspect/apply tables=%s", request == null ? null : request.tables());
        IntrospectResult result = introspectService.apply(request);
        return Response.status(result.valid() ? Response.Status.OK : Response.Status.BAD_REQUEST).entity(result).build();
    }

    @POST
    @Path("/batch")
    @RolesAllowed({"Admin", "admin", "Manager", "manager"})
    public Response startBatch(IntrospectBatchRequest request) throws Exception {
        Log.info("POST introspect/batch");
        try {
            IntrospectJob job = batchService.start(request, caller());
            return Response.status(Response.Status.ACCEPTED).entity(job).build();
        } catch (IntrospectBatchService.ConflictException e) {
            return Response.status(Response.Status.CONFLICT).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @GET
    @Path("/batch")
    @RolesAllowed({"Admin", "admin", "Manager", "manager"})
    public List<IntrospectJob> listBatch() throws Exception {
        return batchService.listJobs();
    }

    @GET
    @Path("/batch/{uuid}")
    @RolesAllowed({"Admin", "admin", "Manager", "manager"})
    public Response getBatch(@PathParam("uuid") String uuid) throws Exception {
        IntrospectJob job = batchService.getJob(uuid);
        if (job == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(job).build();
    }

    @GET
    @Path("/batch/{uuid}/logs")
    @RolesAllowed({"Admin", "admin", "Manager", "manager"})
    public Response getBatchLogs(@PathParam("uuid") String uuid, @QueryParam("after") String after) throws Exception {
        if (batchService.getJob(uuid) == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        List<IntrospectJobLog> logs = batchService.logs(uuid, after);
        return Response.ok(logs).build();
    }

    @POST
    @Path("/batch/{uuid}/cancel")
    @RolesAllowed({"Admin", "admin", "Manager", "manager"})
    public Response cancelBatch(@PathParam("uuid") String uuid) throws Exception {
        IntrospectJob job = batchService.cancel(uuid);
        if (job == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(job).build();
    }

    private String caller() {
        return identity == null || identity.isAnonymous() || identity.getPrincipal() == null
                ? null
                : identity.getPrincipal().getName();
    }
}
