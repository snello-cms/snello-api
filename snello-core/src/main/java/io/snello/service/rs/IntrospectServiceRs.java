package io.snello.service.rs;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.management.SnelloPlane;
import io.snello.model.pojo.IntrospectRequest;
import io.snello.model.pojo.IntrospectResult;
import io.snello.service.IntrospectService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static io.snello.management.AppConstants.INTROSPECT_PATH;

/**
 * Reverse schema authoring: physical tables → metadata + fielddefinitions ({@code already_exist=true}).
 * <ul>
 *     <li>{@code POST /api/introspect/preview}: dry run; always 200 with {@link IntrospectResult}</li>
 *     <li>{@code POST /api/introspect/apply}: persist rows; 400 when invalid. Never runs DDL.</li>
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
}
