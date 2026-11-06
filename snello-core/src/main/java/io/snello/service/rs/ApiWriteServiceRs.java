package io.snello.service.rs;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.management.SnelloPlane;
import io.snello.model.Metadata;
import io.snello.service.ApiService;
import io.snello.util.TableKeyUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.util.Map;
import java.util.Objects;

import static io.snello.management.AppConstants.*;
import static io.snello.util.ApiRequestUtils.*;
import static jakarta.ws.rs.core.Response.ok;
import static jakarta.ws.rs.core.Response.serverError;

/**
 * Write side of the generic data API ({@code POST/PUT/DELETE /api/{table}...}). Control plane only: on the query
 * plane this class is not registered, so writes answer 405 (the read methods of {@link ApiServiceRs} still match
 * the path).
 */
@Path(API_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class ApiWriteServiceRs {

    @Inject
    ApiService apiService;

    @Context
    UriInfo uriInfo;

    @Inject
    SecurityContext securityContext;

    public ApiWriteServiceRs() {
    }

    @POST
    @Path(TABLE_PATH_PARAM)
    @RunOnVirtualThread
    public Response post(Map<String, Object> map, @NotNull @PathParam("table") String table) throws Exception {
        logRequest(POST.class.getSimpleName(), uriInfo, securityContext);
        Metadata metadata = apiService.metadataWithFields(table);
        if (metadata == null) {
            Log.infof("post table=%s: no metadata -> 404", table);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        String key = metadata.table_key;
        if (metadata.api_protected) {
            if (!isAdminOrManager(securityContext)) {
                if (!isAuthenticated(securityContext)) {
                    Log.infof("post table=%s: anonymous on protected table -> 401", table);
                    return Response.status(Response.Status.UNAUTHORIZED).build();
                }
                map.put(metadata.username_field, username(securityContext));
            } else {
                Log.info("admin or manager");
            }
        } else {
            Log.info("api NOT protected");
        }
        TableKeyUtils.generateUUid(map, metadata, apiService);
        map = apiService.create(table, map, key);
        Log.infof("post table=%s key=%s id=%s: created", table, key, map == null ? null : map.get(key));
        return ok(map).build();
    }

    @PUT
    @Path(TABLE_PATH_PARAM + UUID_PATH_PARAM)
    @RunOnVirtualThread
    public Response put(Map<String, Object> map, @NotNull @PathParam("table") String table, @NotNull @PathParam("uuid") String uuid) throws Exception {
        logRequest(PUT.class.getSimpleName(), uriInfo, securityContext);
        Metadata metadata = apiService.metadataWithFields(table);
        if (metadata == null) {
            Log.infof("put table=%s uuid=%s: no metadata -> 404", table, uuid);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        boolean renewSlug = TableKeyUtils.isSlug(metadata);
        String key = apiService.table_key(table);
        if (renewSlug) {
            String fieldSluggable = apiService.slugField(table);
            String toSlugValue = (String) map.get(fieldSluggable);
            String slugged = TableKeyUtils.createSlug(toSlugValue);
            Log.info("toSlugValue: " + toSlugValue + ", old slug: " + uuid);
            if (!uuid.equals(slugged)) {
                Log.info("renew slug");
                TableKeyUtils.generateUUid(map, metadata, apiService);
            } else {
                Log.info(" slug is the same!!");
            }
        }
        if (metadata.api_protected) {
            if (!isAuthenticated(securityContext)) {
                Log.infof("put table=%s uuid=%s: anonymous on protected table -> 401", table, uuid);
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }
            if (!isAdminOrManager(securityContext)) {
                map.put(metadata.username_field, username(securityContext));
            } else {
                Log.info("admin or manager");
            }
        }
        map = apiService.merge(table, map, uuid, key);
        return ok(map).build();
    }

    @DELETE
    @Path(TABLE_PATH_PARAM + UUID_PATH_PARAM)
    @RunOnVirtualThread
    public Response delete(@NotNull @PathParam("table") String table, @NotNull @PathParam("uuid") String uuid) throws Exception {
        logRequest(DELETE.class.getSimpleName(), uriInfo, securityContext);
        Metadata metadata = apiService.metadata(table);
        if (metadata == null) {
            Log.infof("delete table=%s uuid=%s: no metadata -> 404", table, uuid);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        String key = metadata.table_key;
        if (metadata.api_protected) {
            if (!isAdminOrManager(securityContext)) {
                if (!isAuthenticated(securityContext)) {
                    Log.infof("delete table=%s uuid=%s: anonymous on protected table -> 401", table, uuid);
                    return Response.status(Response.Status.UNAUTHORIZED).build();
                }
                var result = apiService.fetch(uriInfo.getQueryParameters(), table, uuid, key);
                if (result == null) {
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
                Object owner = result.get(metadata.username_field);
                String currentUser = username(securityContext);
                if (!Objects.equals(owner, currentUser)) {
                    Log.infof("delete table=%s uuid=%s: owner=%s caller=%s -> 403", table, uuid, owner, currentUser);
                    return Response.status(Response.Status.FORBIDDEN).build();
                }
            } else {
                Log.info("admin or manager");
            }
        }
        boolean result = apiService.delete(table, uuid, key);
        Log.infof("delete table=%s uuid=%s key=%s: result=%s", table, uuid, key, result);
        if (result) return ok().build();
        return serverError().build();
    }
}
