package io.snello.service.rs;


import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.model.Metadata;
import io.snello.service.ApiService;
import io.snello.service.MetadataService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.MultivaluedHashMap;

import static io.snello.management.AppConstants.DATALIST_PATH;
import static io.snello.management.AppConstants.FIELD_DEFINITIONS;
import static jakarta.ws.rs.core.Response.ok;

@Path(DATALIST_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
public class DataListServiceRs {

    @Inject
    MetadataService metadataService;

    @Inject
    ApiService apiService;

    @GET
    @Path("/names")
    
    public Response names() throws Exception {
        return ok(metadataService.names()).build();
    }

    @GET
    @Path("/metadata/{name}")
    
    public Response metadata(@PathParam("name") @NotNull String name) throws Exception {
        io.snello.model.Metadata metadata = metadataService.metadata(name);
        if (metadata != null) {
            // Esclude table_key dalla risposta al frontend
            metadata.table_key = null;
        }
        return ok(metadata).build();
    }

    @GET
    @Path("/metadata/{name}/fielddefinitions")
    
    public Response fielddefinitions(@PathParam("name") @NotNull String name) throws Exception {
        Metadata metadata = metadataService.metadata(name);
        if (metadata == null || metadata.uuid == null) {
            return ok(new java.util.ArrayList<>()).build();
        }
        MultivaluedHashMap<String, String> params = new MultivaluedHashMap<>();
        params.putSingle("metadata_uuid", metadata.uuid);
        return ok(apiService.list(FIELD_DEFINITIONS, params, "name asc", 1000, 0)).build();
    }

    @GET
    @Path("/metadata/{name}/condition")
    
    public Response conditions(@PathParam("name") @NotNull String name) throws Exception {
        return ok(metadataService.conditions(name)).build();
    }

}
