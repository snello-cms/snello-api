package io.snello.service.rs;

import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.model.Metadata;
import io.snello.service.ApiService;
import io.snello.util.TableKeyUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.util.*;

import static io.snello.management.AppConstants.*;
import static jakarta.ws.rs.core.Response.ok;
import static jakarta.ws.rs.core.Response.serverError;

@Path(API_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
public class ApiServiceRs {


    @Inject
    ApiService apiService;

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
        if (sort != null || limit != null || start != null) {
            Log.infof("pagination sort=%s limit=%s start=%s", sort, limit, start);
        }
        debug(GET.class.getName());
        debugMe();
        int l = limit == null ? 10 : Integer.parseInt(limit);
        int s = start == null ? 0 : Integer.parseInt(start);
        Metadata metadata = apiService.metadata(table);
        if (metadata != null && metadata.api_protected) {
            MultivaluedMap<String, String> parametersMap = null;
            if (uriInfo.getQueryParameters() != null) {
                parametersMap = new MultivaluedHashMap<>(uriInfo.getQueryParameters());
            } else {
                parametersMap = new MultivaluedHashMap<>();
            }
            if (!isAdminOrManager()) {
                if (!isAuthenticated()) {
                    Log.info("Unauthorized");
                    return Response.status(Response.Status.UNAUTHORIZED).build();
                }
                parametersMap.put(metadata.username_field, List.of(securityContext.getUserPrincipal().getName()));
            } else {
                Log.info("admin or manager");
            }
        }
        long count = apiService.count(table, uriInfo);
        return ok(apiService.list(table, uriInfo.getQueryParameters(), sort, l, s)).header(SIZE_HEADER_PARAM, "" + count).header(TOTAL_COUNT_HEADER_PARAM, "" + count).build();
    }


    @GET
    @Path(TABLE_PATH_PARAM + UUID_PATH_PARAM)
    @RunOnVirtualThread
    public Response fetch(@NotNull @PathParam("table") String table, @NotNull @PathParam("uuid") String uuid) throws Exception {
        debug(GET.class.getName());
        debugMe();
        Metadata metadata = apiService.metadata(table);
        if (metadata == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        String key = metadata.table_key;
        var result = apiService.fetch(uriInfo.getQueryParameters(), table, uuid, key);
        if (result == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (metadata != null && metadata.api_protected) {
            if (!isAuthenticated()) {
                Log.info("Unauthorized");
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }
            if (!isAdminOrManager()) {
                Object owner = result.get(metadata.username_field);
                String currentUser = securityContext.getUserPrincipal().getName();
                if (!Objects.equals(owner, currentUser)) {
                    throw new Exception("Unauthorized");
                }
            } else {
                Log.info("admin or manager");
            }
        }
        return ok(result).build();
    }


    @GET
    @Path(TABLE_PATH_PARAM + UUID_PATH_PARAM + EXTRA_PATH_PARAM)
    @RunOnVirtualThread
    public Response get(@NotNull @PathParam("table") String table, @NotNull @PathParam("uuid") String uuid, @NotNull @PathParam("path") String path, @QueryParam(SORT_PARAM) String sort, @QueryParam(LIMIT_PARAM) String limit, @QueryParam(START_PARAM) String start) throws Exception {
        debug(GET.class.getName());
        debugMe();
        Metadata metadata = apiService.metadata(table);
        if (path == null) {
            throw new Exception(MSG_PATH_IS_EMPTY);
        }
        if (start == null) {
            start = _0;
        }
        if (limit == null) {
            limit = _10;
        }
        Log.info("path accessorio: " + path);
        if (path.contains("/")) {
            String[] pars = path.split(BASE_PATH);
            if (pars.length > 1) {
                return ok(apiService.fetch(uriInfo.getQueryParameters(), pars[0], pars[1], UUID)).build();
            } else {
                MultivaluedMap<String, String> parametersMap = null;
                if (uriInfo.getQueryParameters() != null) {
                    parametersMap = new MultivaluedHashMap<>(uriInfo.getQueryParameters());
                } else {
                    parametersMap = new MultivaluedHashMap<>();
                }
                if (metadata != null && metadata.api_protected) {
                    if (!isAdminOrManager()) {
                        if (!isAuthenticated()) {
                            Log.info("Unauthorized");
                            return Response.status(Response.Status.UNAUTHORIZED).build();
                        }
                        parametersMap.put(metadata.username_field, List.of(securityContext.getUserPrincipal().getName()));
                    } else {
                        Log.info("admin or manager");
                    }
                }
                return ok(apiService.list(pars[0], parametersMap, sort, Integer.valueOf(limit), Integer.valueOf(start))).build();
            }
        } else {
            MultivaluedMap<String, String> parametersMap = null;
            if (uriInfo.getQueryParameters() != null) {
                parametersMap = uriInfo.getQueryParameters();
            } else {
                parametersMap = new MultivaluedHashMap<>();
            }
            parametersMap.put(table + "_id", Collections.singletonList(uuid));
            parametersMap.put("join_table", List.of(table + "_" + path));
            return ok(apiService.list(path, parametersMap, sort, Integer.valueOf(limit), Integer.valueOf(start))).build();
        }
    }


    @POST
    @Path(TABLE_PATH_PARAM)
    @RunOnVirtualThread
    public Response post(Map<String, Object> map, @NotNull @PathParam("table") String table) throws Exception {
        debug(POST.class.getName());
        debugMe();
        Metadata metadata = apiService.metadataWithFields(table);
        if (metadata == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        String key = metadata.table_key;
        if (metadata != null && metadata.api_protected) {
            if (!isAdminOrManager()) {
                if (!isAuthenticated()) {
                    Log.info("Unauthorized");
                    return Response.status(Response.Status.UNAUTHORIZED).build();
                }
                map.put(metadata.username_field, securityContext.getUserPrincipal().getName());
            } else {
                Log.info("admin or manager");
            }
        } else {
            Log.info("api NOT protected");
        }
        TableKeyUtils.generateUUid(map, metadata, apiService);
        map = apiService.create(table, map, key);
        return ok(map).build();
    }

    @PUT
    @Path(TABLE_PATH_PARAM + UUID_PATH_PARAM)
    @RunOnVirtualThread
    public Response put(Map<String, Object> map, @NotNull @PathParam("table") String table, @NotNull @PathParam("uuid") String uuid) throws Exception {
        debug(PUT.class.getName());
        debugMe();
        Metadata metadata = apiService.metadataWithFields(table);
        if (metadata == null) {
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
            if (!isAuthenticated()) {
                Log.info("Unauthorized");
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }
            if (!isAdminOrManager()) {
                map.put(metadata.username_field, securityContext.getUserPrincipal().getName());
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
        debug(DELETE.class.getName());
        debugMe();
        Metadata metadata = apiService.metadata(table);
        if (metadata == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        String key = metadata.table_key;
        if (metadata != null && metadata.api_protected) {
            if (!isAdminOrManager()) {
                if (!isAuthenticated()) {
                    Log.info("Unauthorized");
                    return Response.status(Response.Status.UNAUTHORIZED).build();
                }
                var result = apiService.fetch(uriInfo.getQueryParameters(), table, uuid, key);
                if (result == null) {
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
                Object owner = result.get(metadata.username_field);
                String currentUser = securityContext.getUserPrincipal().getName();
                if (!Objects.equals(owner, currentUser)) {
                    throw new Exception("Unauthorized");
                }
            } else {
                Log.info("admin or manager");
            }
        }
        boolean result = apiService.delete(table, uuid, key);
        if (result) return ok().build();
        return serverError().build();
    }

    private boolean isAuthenticated() {
        return securityContext != null && securityContext.getUserPrincipal() != null;
    }


    private boolean isAdminOrManager() {
        return securityContext != null && (securityContext.isUserInRole("admin") || securityContext.isUserInRole("Admin") || securityContext.isUserInRole("manager") || securityContext.isUserInRole("Manager"));
    }


    private void debugMe() {
        if (securityContext == null || securityContext.getUserPrincipal() == null) {
            Log.info("SECURITY user=<anonymous> roles={admin=false,Admin=false,user=false,User=false,manager=false,Manager=false}");
            return;
        }
        Log.infof(
                "SECURITY user=%s roles={admin=%s,Admin=%s,user=%s,User=%s,manager=%s,Manager=%s}",
                securityContext.getUserPrincipal().getName(),
                securityContext.isUserInRole("admin"),
                securityContext.isUserInRole("Admin"),
                securityContext.isUserInRole("user"),
                securityContext.isUserInRole("User"),
                securityContext.isUserInRole("manager"),
                securityContext.isUserInRole("Manager")
        );
    }


    private void debug(String method) {
        String username = (securityContext != null && securityContext.getUserPrincipal() != null)
            ? securityContext.getUserPrincipal().getName()
            : "<anonymous>";
        String pathParams = uriInfo.getPathParameters() == null ? "{}" : uriInfo.getPathParameters().toString();
        String queryParams = uriInfo.getQueryParameters() == null ? "{}" : uriInfo.getQueryParameters().toString();
        Log.infof("REQUEST method=%s path=%s user=%s pathParams=%s queryParams=%s",
            method,
            uriInfo.getPath(),
            username,
            pathParams,
            queryParams);
    }

}
