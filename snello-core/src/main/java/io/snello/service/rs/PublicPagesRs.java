package io.snello.service.rs;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.management.SnelloPlane;
import io.snello.service.ApiService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.snello.management.AppConstants.API_PATH;
import static io.snello.management.AppConstants.PUBLIC_PAGES_PATH;
import static io.snello.management.AppConstants.WORKFLOW_PAGES;

/**
 * Workflow pages, outside /api because they are pages:
 * <pre>
 * GET /p/{name}?task={uuid}    the page `name` of table workflowpages, in a shell that loads the page client
 * GET /p/task?task={uuid}      built-in generic page: the record's fields + the task's decision buttons
 * GET /p/_/snello-page.js      the page client: window.snello (login, REST, task context, decide / undo)
 * </pre>
 * The HTML carries no data: the page logs in with Keycloak (public client) and reads the task context through the
 * API with the viewer's token, where every rule is enforced. Pages are written by Admins / Managers only.
 */
@Path(PUBLIC_PAGES_PATH)
@ApplicationScoped
@RunOnVirtualThread
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class PublicPagesRs {

    private static final String GENERIC = "task";
    private static final String GENERIC_BODY = """
            <div id="snello-task"></div>
            <script>snello.renderTask(document.getElementById('snello-task'));</script>
            """;

    @Inject
    ApiService apiService;

    // Optional, not defaultValue = "": MicroProfile Config treats an empty default as missing (startup failure)
    @ConfigProperty(name = "quarkus.oidc.auth-server-url")
    Optional<String> oidcServerUrl;

    @ConfigProperty(name = "snello.pages.keycloak.url")
    Optional<String> keycloakUrl;

    @ConfigProperty(name = "snello.pages.keycloak.realm")
    Optional<String> keycloakRealm;

    /** a PUBLIC client whose redirect URIs / web origins allow this host (e.g. the admin's snello-admin) */
    @ConfigProperty(name = "snello.pages.keycloak.client-id", defaultValue = "snello-admin")
    String keycloakClientId;

    private final JsonMapper json = JsonMapper.builder().build();
    private volatile String clientJs;

    @GET
    @Path("/{name}")
    public Response page(@PathParam("name") String name) throws Exception {
        if (!WorkflowPagesServiceRs.NAME.matcher(name).matches()) {
            return notFound(name);
        }
        String title;
        String body;
        if (GENERIC.equals(name)) {
            title = "Task";
            body = GENERIC_BODY;
        } else {
            MultivaluedMap<String, String> q = new MultivaluedHashMap<>();
            q.add("name", name);
            List<Map<String, Object>> rows = apiService.list(WORKFLOW_PAGES, q, null, 1, 0);
            if (rows.isEmpty()) {
                return notFound(name);
            }
            Map<String, Object> p = rows.get(0);
            title = Optional.ofNullable(p.get("title")).map(Object::toString).orElse(name);
            body = Optional.ofNullable(p.get("body")).map(Object::toString).filter(s -> !s.isBlank()).orElse(GENERIC_BODY);
        }
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("name", name);
        cfg.put("title", title);
        cfg.put("api", API_PATH);
        cfg.put("keycloak", keycloak());
        String html = "<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
                + "<title>" + escape(title) + "</title>\n"
                + "<script>window.SNELLO_PAGE = " + json.writeValueAsString(cfg).replace("</", "<\\/") + ";</script>\n"
                + "<script src=\"" + PUBLIC_PAGES_PATH + "/_/snello-page.js\"></script>\n"
                + "</head>\n<body>\n" + body + "\n</body>\n</html>\n";
        Log.infof("page /p/%s served (%d chars, keycloak client %s)", name, html.length(), keycloakClientId);
        return Response.ok(html, "text/html; charset=utf-8")
                .header("Cache-Control", "no-store")
                .header("Referrer-Policy", "no-referrer")
                .header("X-Frame-Options", "DENY")
                .header("X-Content-Type-Options", "nosniff")
                .build();
    }

    @GET
    @Path("/_/snello-page.js")
    public Response client() throws IOException {
        if (clientJs == null) {
            try (InputStream in = getClass().getResourceAsStream("/io/snello/pages/snello-page.js")) {
                if (in == null) {
                    Log.error("snello-page.js missing from the classpath");
                    return Response.status(500).build();
                }
                clientJs = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return Response.ok(clientJs, "text/javascript; charset=utf-8").header("Cache-Control", "no-cache").build();
    }

    /** url + realm from snello.pages.keycloak.*, else from quarkus.oidc.auth-server-url (https://host/realms/x). */
    private Map<String, String> keycloak() {
        String url = keycloakUrl.orElse(null);
        String realm = keycloakRealm.orElse(null);
        String oidc = oidcServerUrl.orElse("");
        int i = oidc.indexOf("/realms/");
        if (url == null && i > 0) {
            url = oidc.substring(0, i);
        }
        if (realm == null && i > 0) {
            realm = oidc.substring(i + "/realms/".length()).replaceAll("/.*$", "");
        }
        Map<String, String> k = new LinkedHashMap<>();
        k.put("url", url);
        k.put("realm", realm);
        k.put("clientId", keycloakClientId);
        return k;
    }

    private static Response notFound(String name) {
        Log.infof("page /p/%s: not found", name);
        return Response.status(404).entity("<!doctype html><title>Not found</title><p>Page not found.</p>")
                .type("text/html; charset=utf-8").build();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
