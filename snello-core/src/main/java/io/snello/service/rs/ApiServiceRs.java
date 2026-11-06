package io.snello.service.rs;

import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.management.SnelloPlane;
import io.snello.model.pojo.EntityQueryView;
import io.snello.service.ApiService;
import io.snello.service.EntityCatalog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static io.snello.management.AppConstants.*;
import static io.snello.util.ApiRequestUtils.*;
import static jakarta.ws.rs.core.Response.ok;

/**
 * Read side of the generic data API ({@code GET /api/{table}...}). Shipped by both planes.
 * Writes live in {@link ApiWriteServiceRs} (control plane only).
 * <p>
 * Row-level security ({@code api_protected} + {@code username_field}) is applied to the <em>target</em> table
 * of every branch, including the sub-path variants of {@link #get}.
 * On the query plane only declared resources (metadata entities / select queries) are served, so system
 * tables ({@code metadatas}, {@code actions}, ...) are never reachable through the generic API.
 */
@Path(API_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
public class ApiServiceRs {

    @Inject
    ApiService apiService;

    @Inject
    EntityCatalog catalog;

    @ConfigProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, defaultValue = "true")
    boolean controlPlane;

    @Context
    UriInfo uriInfo;

    @Inject
    SecurityContext securityContext;

    public ApiServiceRs() {
    }


    @GET
    @Path(TABLE_PATH_PARAM)
    @RunOnVirtualThread
    public Response list(@NotNull @PathParam("table") String table, @QueryParam(SORT_PARAM) String sort, @QueryParam(LIMIT_PARAM) String limit, @QueryParam(START_PARAM) String start) throws Exception {
        logRequest(GET.class.getSimpleName(), uriInfo, securityContext);
        Log.infof("list table=%s sort=%s limit=%s start=%s", table, sort, limit, start);
        if (!servable(table)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        int l = limit == null ? 10 : Integer.parseInt(limit);
        int s = start == null ? 0 : Integer.parseInt(start);
        MultivaluedMap<String, String> parameters = copyQueryParameters();
        Response denied = applyRowFilter(table, parameters);
        if (denied != null) {
            return denied;
        }
        long count = apiService.count(table, parameters);
        List<Map<String, Object>> rows = apiService.list(table, parameters, sort, l, s);
        Log.infof("list table=%s count=%d returned=%d", table, count, rows == null ? 0 : rows.size());
        return ok(rows).header(SIZE_HEADER_PARAM, "" + count).header(TOTAL_COUNT_HEADER_PARAM, "" + count).build();
    }


    @GET
    @Path(TABLE_PATH_PARAM + UUID_PATH_PARAM)
    @RunOnVirtualThread
    public Response fetch(@NotNull @PathParam("table") String table, @NotNull @PathParam("uuid") String uuid) throws Exception {
        logRequest(GET.class.getSimpleName(), uriInfo, securityContext);
        Optional<EntityQueryView> view = catalog.queryView(table);
        if (view.isEmpty()) {
            Log.infof("fetch table=%s uuid=%s: no metadata -> 404", table, uuid);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return fetchGuarded(view.get(), uuid, view.get().tableKey());
    }


    @GET
    @Path(TABLE_PATH_PARAM + UUID_PATH_PARAM + EXTRA_PATH_PARAM)
    @RunOnVirtualThread
    public Response get(@NotNull @PathParam("table") String table, @NotNull @PathParam("uuid") String uuid, @NotNull @PathParam("path") String path, @QueryParam(SORT_PARAM) String sort, @QueryParam(LIMIT_PARAM) String limit, @QueryParam(START_PARAM) String start) throws Exception {
        logRequest(GET.class.getSimpleName(), uriInfo, securityContext);
        if (path == null) {
            throw new Exception(MSG_PATH_IS_EMPTY);
        }
        int l = limit == null ? Integer.parseInt(_10) : Integer.parseInt(limit);
        int s = start == null ? Integer.parseInt(_0) : Integer.parseInt(start);
        Log.infof("get table=%s uuid=%s path=%s", table, uuid, path);

        if (path.contains("/")) {
            String[] pars = path.split(BASE_PATH);
            String target = pars[0];
            if (!servable(target)) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            if (pars.length > 1) {
                Log.infof("get branch=fetch-other target=%s id=%s", target, pars[1]);
                Optional<EntityQueryView> view = catalog.queryView(target);
                if (view.isPresent()) {
                    return fetchGuarded(view.get(), pars[1], UUID);
                }
                return ok(apiService.fetch(uriInfo.getQueryParameters(), target, pars[1], UUID)).build();
            }
            Log.infof("get branch=list-other target=%s", target);
            MultivaluedMap<String, String> parameters = copyQueryParameters();
            Response denied = applyRowFilter(target, parameters);
            if (denied != null) {
                return denied;
            }
            return ok(apiService.list(target, parameters, sort, l, s)).build();
        }

        Log.infof("get branch=join-list target=%s join_table=%s_%s", path, table, path);
        if (!servable(path)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        MultivaluedMap<String, String> parameters = copyQueryParameters();
        parameters.put(table + "_id", List.of(uuid));
        parameters.put("join_table", List.of(table + "_" + path));
        Response denied = applyRowFilter(path, parameters);
        if (denied != null) {
            return denied;
        }
        return ok(apiService.list(path, parameters, sort, l, s)).build();
    }

    // ------------------------------------------------------------------ guards

    /**
     * Control plane keeps the legacy "any table" behaviour (admin SPA relies on it); the query plane serves only
     * declared resources.
     */
    private boolean servable(String table) throws Exception {
        if (controlPlane) {
            return true;
        }
        boolean declared = catalog.isDeclared(table);
        if (!declared) {
            Log.infof("query plane: table=%s is not a declared metadata/selectquery -> 404", table);
        }
        return declared;
    }

    /**
     * For a protected table: anonymous → 401; non-admin → force {@code username_field = caller}.
     *
     * @return an error response, or {@code null} when the request may proceed
     */
    private Response applyRowFilter(String table, MultivaluedMap<String, String> parameters) throws Exception {
        Optional<EntityQueryView> view = catalog.queryView(table);
        if (view.isEmpty() || !view.get().apiProtected()) {
            return null;
        }
        if (isAdminOrManager(securityContext)) {
            Log.infof("row filter table=%s: admin or manager, no filter", table);
            return null;
        }
        if (!isAuthenticated(securityContext)) {
            Log.infof("row filter table=%s: anonymous on protected table -> 401", table);
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        if (view.get().rowLevelSecured()) {
            String user = username(securityContext);
            parameters.put(view.get().usernameField(), List.of(user));
            Log.infof("row filter table=%s: %s=%s", table, view.get().usernameField(), user);
        } else {
            Log.warnf("row filter table=%s: api_protected without username_field, authenticated users see all rows", table);
        }
        return null;
    }

    private Response fetchGuarded(EntityQueryView view, String id, String key) throws Exception {
        if (view.apiProtected() && !isAuthenticated(securityContext)) {
            Log.infof("fetch table=%s id=%s: anonymous on protected table -> 401", view.tableName(), id);
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        Map<String, Object> row = apiService.fetch(uriInfo.getQueryParameters(), view.tableName(), id, key);
        if (row == null) {
            Log.infof("fetch table=%s id=%s key=%s: not found", view.tableName(), id, key);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (view.rowLevelSecured() && !isAdminOrManager(securityContext)) {
            Object owner = row.get(view.usernameField());
            String user = username(securityContext);
            if (!Objects.equals(owner, user)) {
                Log.infof("fetch table=%s id=%s: owner=%s caller=%s -> 403", view.tableName(), id, owner, user);
                return Response.status(Response.Status.FORBIDDEN).build();
            }
        }
        return ok(row).build();
    }

    private MultivaluedMap<String, String> copyQueryParameters() {
        MultivaluedMap<String, String> source = uriInfo.getQueryParameters();
        return source == null ? new MultivaluedHashMap<>() : new MultivaluedHashMap<>(source);
    }
}
