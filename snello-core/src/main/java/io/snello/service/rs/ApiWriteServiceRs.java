package io.snello.service.rs;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.util.FieldDefinitionUtils;
import io.snello.management.SnelloPlane;
import io.snello.model.Metadata;
import io.snello.service.ApiService;
import io.snello.util.RowValidator;
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
        Response notAllowed = requireWriteRole(metadata, table, "post", null);
        if (notAllowed != null) {
            return notAllowed;
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
        dropVirtualValues(metadata, map, table);
        RowValidator.validate(metadata, map, true);
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
        Response notAllowed = requireWriteRole(metadata, table, "put", uuid);
        if (notAllowed != null) {
            return notAllowed;
        }
        String key = apiService.table_key(table);
        // Ownership first: the owner value must be fixed before the slug is derived (lovers slugs on username).
        if (metadata.api_protected) {
            if (!isAuthenticated(securityContext)) {
                Log.infof("put table=%s uuid=%s: anonymous on protected table -> 401", table, uuid);
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }
            if (!isAdminOrManager(securityContext)) {
                Response denied = requireOwner(metadata, table, uuid, key);
                if (denied != null) {
                    return denied;
                }
                map.put(metadata.username_field, username(securityContext));
            } else {
                Log.infof("put table=%s uuid=%s: admin or manager, no owner check", table, uuid);
            }
        }
        // An update never changes the key: the row stays at the URL it was read from (links, joins, documents and
        // the admin's reload all point at it). Slug keys are generated once, on create; a key sent in the body is
        // ignored. Previously the slug was re-derived on every save and the row moved, e.g. mario-1 -> mario-2.
        Object bodyKey = map.get(key);
        if (bodyKey != null && !uuid.equals(String.valueOf(bodyKey))) {
            Log.warnf("put table=%s uuid=%s: body %s=%s ignored, the key cannot change on update", table, uuid, key, bodyKey);
        }
        map.put(key, uuid);
        dropVirtualValues(metadata, map, table);
        RowValidator.validate(metadata, map, false);
        map = apiService.merge(table, map, uuid, key);
        return ok(map).build();
    }

    /**
     * Non-admin write on a protected table: the target row must exist and belong to the caller. Without this a user
     * could PUT another user's row (it was only re-stamped with the caller's username, i.e. taken over).
     *
     * @return an error response, or {@code null} when the caller owns the row
     */
    /**
     * Table write permission ({@code metadatas.write_roles}, e.g. "Admin,Manager"): creating, changing and deleting
     * rows needs one of those roles. Empty = any authenticated user, as before. Reads are not affected (a catalog can
     * be public to read and writable only by the staff).
     *
     * @return the refusal (401 anonymous, 403 without the role), or null when the write may go on
     */
    private Response requireWriteRole(Metadata metadata, String table, String operation, String uuid) {
        if (metadata.write_roles == null || metadata.write_roles.isBlank()) {
            return null;
        }
        if (!isAuthenticated(securityContext)) {
            Log.infof("%s table=%s uuid=%s: anonymous, write needs %s -> 401", operation, table, uuid, metadata.write_roles);
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        for (String role : metadata.write_roles.split(",")) {
            if (!role.isBlank() && securityContext.isUserInRole(role.trim())) {
                Log.debugf("%s table=%s uuid=%s: caller has write role %s", operation, table, uuid, role.trim());
                return null;
            }
        }
        Log.infof("%s table=%s uuid=%s: caller %s has none of the write roles %s -> 403", operation, table, uuid,
                username(securityContext), metadata.write_roles);
        return Response.status(Response.Status.FORBIDDEN)
                .entity(Map.of("msg", "only " + metadata.write_roles.trim() + " can change " + table))
                .type(MediaType.APPLICATION_JSON).build();
    }

    /** Virtual fields (button) have no column: a value sent for them (an admin form control) is dropped. */
    private static void dropVirtualValues(Metadata metadata, Map<String, Object> map, String table) {
        java.util.List<String> dropped = FieldDefinitionUtils.stripVirtual(metadata.fields, map);
        if (!dropped.isEmpty()) {
            Log.infof("write table=%s: virtual field values ignored %s", table, dropped);
        }
    }

    private Response requireOwner(Metadata metadata, String table, String uuid, String key) throws Exception {
        String user = username(securityContext);
        if (user == null || user.isBlank()) {
            Log.warnf("write table=%s uuid=%s: authenticated caller without a username -> 403", table, uuid);
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        Map<String, Object> existing = apiService.fetch(null, table, uuid, key);
        if (existing == null) {
            Log.infof("write table=%s uuid=%s: not found -> 404", table, uuid);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Object owner = existing.get(metadata.username_field);
        if (!Objects.equals(owner, user)) {
            Log.infof("write table=%s uuid=%s: owner=%s caller=%s -> 403", table, uuid, owner, user);
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        Log.infof("write table=%s uuid=%s: caller %s owns the row", table, uuid, user);
        return null;
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
        Response notAllowed = requireWriteRole(metadata, table, "delete", uuid);
        if (notAllowed != null) {
            return notAllowed;
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
