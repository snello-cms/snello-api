package io.snello.util;

import io.quarkus.logging.Log;
import io.snello.model.pojo.RowProtection;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;

import java.util.List;

/**
 * Caller / request helpers shared by the {@code /api/{table}} read and write resources.
 */
public final class ApiRequestUtils {

    private ApiRequestUtils() {
    }

    public static boolean isAuthenticated(SecurityContext sc) {
        return sc != null && sc.getUserPrincipal() != null;
    }

    public static String username(SecurityContext sc) {
        return isAuthenticated(sc) ? sc.getUserPrincipal().getName() : null;
    }

    public static boolean isAdminOrManager(SecurityContext sc) {
        return sc != null && (sc.isUserInRole("admin") || sc.isUserInRole("Admin")
                || sc.isUserInRole("manager") || sc.isUserInRole("Manager"));
    }

    /**
     * Row-level security of a protected resource (metadata, select query, print template): anonymous → 401;
     * non-admin → {@code parameters[username_field] = caller}. Admin / Manager see every row.
     *
     * @return an error response, or {@code null} when the request may proceed (parameters possibly changed)
     */
    public static Response rowFilter(RowProtection protection, String resource, MultivaluedMap<String, String> parameters,
                                     SecurityContext sc) {
        if (protection == null || !protection.apiProtected()) {
            return null;
        }
        if (isAdminOrManager(sc)) {
            Log.infof("row filter %s: admin or manager, no filter", resource);
            return null;
        }
        if (!isAuthenticated(sc)) {
            Log.infof("row filter %s: anonymous on protected resource -> 401", resource);
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        if (protection.rowLevelSecured()) {
            String user = username(sc);
            if (user == null || user.isBlank()) {
                // an empty value is skipped by ParamUtils.where, which would drop the owner filter entirely
                Log.warnf("row filter %s: authenticated caller without a username -> 403", resource);
                return Response.status(Response.Status.FORBIDDEN).build();
            }
            if (SqlSafety.isUnsafeOwnerColumn(protection.usernameField())) {
                Log.errorf("row filter %s: username_field=%s is not a plain column name (operator suffix?) -> 500",
                        resource, protection.usernameField());
                return Response.serverError().build();
            }
            parameters.put(protection.usernameField(), List.of(user));
            Log.infof("row filter %s: %s=%s", resource, protection.usernameField(), user);
        } else {
            Log.warnf("row filter %s: api_protected without username_field, authenticated users see all rows", resource);
        }
        return null;
    }

    public static void logRequest(String method, UriInfo uriInfo, SecurityContext sc) {
        String username = isAuthenticated(sc) ? sc.getUserPrincipal().getName() : "<anonymous>";
        String pathParams = uriInfo.getPathParameters() == null ? "{}" : uriInfo.getPathParameters().toString();
        String queryParams = uriInfo.getQueryParameters() == null ? "{}" : uriInfo.getQueryParameters().toString();
        Log.infof("REQUEST method=%s path=%s user=%s adminOrManager=%s pathParams=%s queryParams=%s",
                method, uriInfo.getPath(), username, isAdminOrManager(sc), pathParams, queryParams);
    }
}
