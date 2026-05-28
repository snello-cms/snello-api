package io.snello.service.rs;

import io.quarkus.logging.Log;

import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.service.ApiService;
import io.snello.service.TemplateService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import static io.snello.management.AppConstants.*;
import static jakarta.ws.rs.core.Response.ok;

@Path(PAGES_PATH)
@ApplicationScoped
@RunOnVirtualThread
public class TemplateServiceRs {


    @Inject
    ApiService apiService;

    @Context
    UriInfo uriInfo;

    @Inject
    TemplateService templateService;

    public TemplateServiceRs() {
    }


    @GET
    @Path(TABLE_PATH_PARAM + UUID_PATH_PARAM)
    @Produces(MediaType.TEXT_HTML)
    @Consumes(MediaType.APPLICATION_JSON)
    
    public Response fetch(@NotNull @PathParam("table") String table,
                          @NotNull @PathParam("uuid") String uuid) throws Exception {
        debug(GET.class.getName());
        String key = apiService.table_key(table);
        var map = apiService.fetch(uriInfo.getQueryParameters(), table, uuid, key);
        var ressult = templateService.parse(map, uriInfo.getQueryParameters());
        return ok(ressult).build();
    }


    private void debug(String method) {
        String pathParams = uriInfo.getPathParameters() == null ? "{}" : uriInfo.getPathParameters().toString();
        String queryParams = uriInfo.getQueryParameters() == null ? "{}" : uriInfo.getQueryParameters().toString();
        Log.infof("REQUEST method=%s path=%s pathParams=%s queryParams=%s",
                method,
                uriInfo.getPath(),
                pathParams,
                queryParams);
    }

}
