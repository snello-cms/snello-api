package io.snello.util;

import io.quarkus.logging.Log;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;

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

    public static void logRequest(String method, UriInfo uriInfo, SecurityContext sc) {
        String username = isAuthenticated(sc) ? sc.getUserPrincipal().getName() : "<anonymous>";
        String pathParams = uriInfo.getPathParameters() == null ? "{}" : uriInfo.getPathParameters().toString();
        String queryParams = uriInfo.getQueryParameters() == null ? "{}" : uriInfo.getQueryParameters().toString();
        Log.infof("REQUEST method=%s path=%s user=%s adminOrManager=%s pathParams=%s queryParams=%s",
                method, uriInfo.getPath(), username, isAdminOrManager(sc), pathParams, queryParams);
    }
}
