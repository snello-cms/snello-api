package io.snello.service.rs;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.management.SnelloPlane;
import io.snello.model.pojo.FlowProxyResponse;
import io.snello.service.flow.FlowClient;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.snello.management.AppConstants.FLOW_PATH;

/**
 * Quarkus Flow monitoring for the admin (Admin / Manager only):
 * <pre>
 * GET  /api/flow/definitions                                  workflows loaded by the runner
 * GET  /api/flow/definitions/{ns}/{name}/{version}            one workflow (JSON), to draw its diagram
 * GET  /api/flow/active                                       runner: instances active in memory
 * GET  /api/flow/instances?namespace=&name=&version=&status=&limit=   flow-bridge: instances with their current task
 * GET  /api/flow/instances/{id}                               flow-bridge: one instance, every task position seen
 * POST /api/flow/instances/{ns}/{name}/{version}/{id}/{suspend|resume|cancel}
 * </pre>
 * Answers are the runner's / flow-bridge's JSON as they came (503 with {"error"} if not configured or unreachable).
 * Path values are checked against the DSL naming rules before they become part of a runner URL.
 */
@Path(FLOW_PATH)
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class FlowServiceRs {

    /** DSL document.namespace / document.name: letters, digits and '-' (no '_'). */
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9-]{1,100}");
    /** semantic version, e.g. 1.0.0 or 1.0.0-beta.1 */
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9.+-]{1,50}");
    /** runner instance ids are ULIDs */
    private static final Pattern INSTANCE = Pattern.compile("[A-Za-z0-9-]{1,64}");
    private static final Pattern STATUS = Pattern.compile("[A-Z_]{1,20}");
    private static final Set<String> ACTIONS = Set.of("suspend", "resume", "cancel");

    @Inject
    FlowClient flowClient;

    @GET
    @Path("/definitions")
    @RolesAllowed({"Admin", "admin", "Manager", "manager"})
    public Response definitions() {
        Log.info("GET flow definitions");
        return respond(flowClient.runner("GET", "/q/flow/definitions"));
    }

    @GET
    @Path("/definitions/{namespace}/{name}/{version}")
    @RolesAllowed({"Admin", "admin", "Manager", "manager"})
    public Response definition(@PathParam("namespace") String namespace, @PathParam("name") String name,
                               @PathParam("version") String version) {
        check("namespace", namespace, NAME);
        check("name", name, NAME);
        check("version", version, VERSION);
        Log.infof("GET flow definition %s:%s:%s", namespace, name, version);
        return respond(flowClient.runner("GET", "/q/flow/definitions/" + namespace + "/" + name + "/" + version));
    }

    @GET
    @Path("/active")
    @RolesAllowed({"Admin", "admin", "Manager", "manager"})
    public Response active() {
        Log.info("GET flow active instances (runner)");
        return respond(flowClient.runner("GET", "/q/flow/instances"));
    }

    @GET
    @Path("/instances")
    @RolesAllowed({"Admin", "admin", "Manager", "manager"})
    public Response instances(@QueryParam("namespace") String namespace, @QueryParam("name") String name,
                              @QueryParam("version") String version, @QueryParam("status") String status,
                              @QueryParam("limit") Integer limit) {
        Map<String, String> q = new LinkedHashMap<>();
        if (namespace != null && !namespace.isBlank()) q.put("namespace", check("namespace", namespace, NAME));
        if (name != null && !name.isBlank()) q.put("name", check("name", name, NAME));
        if (version != null && !version.isBlank()) q.put("version", check("version", version, VERSION));
        if (status != null && !status.isBlank()) q.put("status", check("status", status, STATUS));
        if (limit != null) q.put("limit", String.valueOf(Math.max(1, Math.min(limit, 1000))));
        String query = q.isEmpty() ? "" : "?" + q.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        Log.infof("GET flow instances %s", q);
        return respond(flowClient.bridge("GET", "/instances" + query));
    }

    @GET
    @Path("/instances/{id}")
    @RolesAllowed({"Admin", "admin", "Manager", "manager"})
    public Response instance(@PathParam("id") String id) {
        check("instance id", id, INSTANCE);
        Log.infof("GET flow instance %s", id);
        return respond(flowClient.bridge("GET", "/instances/" + id));
    }

    @POST
    @Path("/instances/{namespace}/{name}/{version}/{id}/{action}")
    @RolesAllowed({"Admin", "admin", "Manager", "manager"})
    public Response control(@PathParam("namespace") String namespace, @PathParam("name") String name,
                            @PathParam("version") String version, @PathParam("id") String id,
                            @PathParam("action") String action) {
        check("namespace", namespace, NAME);
        check("name", name, NAME);
        check("version", version, VERSION);
        check("instance id", id, INSTANCE);
        if (!ACTIONS.contains(action)) {
            throw new BadRequestException("action must be one of " + ACTIONS);
        }
        Log.infof("POST flow %s instance %s of %s:%s:%s", action, id, namespace, name, version);
        return respond(flowClient.runner("POST",
                "/q/flow/exec/" + namespace + "/" + name + "/" + version + "/" + id + "/" + action));
    }

    private static String check(String what, String value, Pattern pattern) {
        if (value == null || !pattern.matcher(value).matches()) {
            Log.warnf("flow: invalid %s '%s'", what, value);
            throw new BadRequestException("invalid " + what);
        }
        return value;
    }

    private static Response respond(FlowProxyResponse r) {
        return Response.status(r.status()).entity(r.body()).type(MediaType.APPLICATION_JSON).build();
    }
}
