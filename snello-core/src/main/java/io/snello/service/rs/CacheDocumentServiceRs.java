package io.snello.service.rs;

import io.quarkus.cache.CacheResult;
import io.smallrye.common.annotation.RunOnVirtualThread;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import static io.snello.management.AppConstants.*;

@Path(CACHE_DOCUMENTS_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Singleton
@RunOnVirtualThread
public class CacheDocumentServiceRs {

    private static final String FETCH_CACHE = "documents-fetch";

    @Inject
    DocumentServiceRs documentServiceRs;

    @GET
    @Path("/{id}")
    @CacheResult(cacheName = FETCH_CACHE)
    
    public Response fetch(@PathParam("id") String id) throws Exception {
        return documentServiceRs.fetch(id);
    }

    @GET
    @Path(UUID_PATH_PARAM + DOWNLOAD_PATH)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    
    public Response download(@PathParam("uuid") @NotNull String uuid,
                             @QueryParam(value = "format") String format) throws Exception {
        return documentServiceRs.download(uuid, format);
    }

    @GET
    @Path(UUID_PATH_PARAM + WEBP_PATH)
    @Consumes("*/*")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    
    public Response webp(@PathParam("uuid") @NotNull String uuid,
                         @QueryParam(value = "format") String format) throws Exception {
        return documentServiceRs.webp(uuid, format);
    }

    @GET
    @Path("/{uuid}/download/{name}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Consumes(MediaType.APPLICATION_JSON)
    
    public Response downloadWithName(@PathParam("uuid") @NotNull String uuid,
                                     @PathParam("name") @NotNull String name) throws Exception {
        return documentServiceRs.downloadWithName(uuid, name);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    
    public Response list(@QueryParam(SORT_PARAM) String sort,
                         @QueryParam(LIMIT_PARAM) String limit,
                         @QueryParam(START_PARAM) String start,
                         @Context UriInfo uriInfo) throws Exception {
        return documentServiceRs.list(sort, limit, start, uriInfo);
    }
}
