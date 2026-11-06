package io.snello.service.rs;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.management.SnelloPlane;
import io.snello.model.pojo.DraftResult;
import io.snello.model.pojo.EntityDraft;
import io.snello.service.DraftService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static io.snello.management.AppConstants.DRAFTS_PATH;

/**
 * AI-authoring contract over HTTP: typed {@link EntityDraft} in, {@link DraftResult} out.
 * <ul>
 *     <li>{@code POST /api/drafts/validate}: dry run (validation, derived rows, DDL preview); always 200</li>
 *     <li>{@code POST /api/drafts/apply?create_table=true}: persist (+ create table); 400 with the result when
 *     invalid. Admin / Manager only.</li>
 * </ul>
 */
@Path(DRAFTS_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class DraftServiceRs {

    @Inject
    DraftService draftService;

    @POST
    @Path("/validate")
    public DraftResult validate(EntityDraft draft) throws Exception {
        Log.infof("POST drafts/validate table=%s", draft == null ? null : draft.table_name());
        return draftService.validate(draft);
    }

    @POST
    @Path("/apply")
    @RolesAllowed({"Admin", "admin", "Manager", "manager"})
    public Response apply(EntityDraft draft, @QueryParam("create_table") @DefaultValue("false") boolean createTable)
            throws Exception {
        Log.infof("POST drafts/apply table=%s create_table=%s", draft == null ? null : draft.table_name(), createTable);
        DraftResult result = draftService.apply(draft, createTable);
        return Response.status(result.valid() ? Response.Status.OK : Response.Status.BAD_REQUEST).entity(result).build();
    }
}
