package io.snello.service.rs;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.api.service.AbstractServiceRs;
import io.snello.management.SnelloPlane;
import io.snello.model.pojo.FlowProxyResponse;
import io.snello.model.pojo.WorkflowDocument;
import io.snello.service.ApiService;
import io.snello.service.flow.FlowClient;
import io.snello.service.flow.RunnerWorkflowYaml;
import io.snello.service.flow.WorkflowYamlValidator;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static io.snello.management.AppConstants.LIMIT_PARAM;
import static io.snello.management.AppConstants.SORT_PARAM;
import static io.snello.management.AppConstants.START_PARAM;
import static io.snello.management.AppConstants.UUID;
import static io.snello.management.AppConstants.WORKFLOW_DEFINITIONS;
import static io.snello.management.AppConstants.WORKFLOW_DEFINITIONS_PATH;

/**
 * Workflow definitions edited in the admin ("Workflows"): Serverless Workflow 1.0 YAML, one row per
 * {@code namespace:name:version}, deployed to the Quarkus Flow runner through flow-bridge (writes Admin / Manager, see
 * AdminConfigWriteFilter).
 * <pre>
 * GET/POST/PUT/DELETE /api/workflowdefinitions[/{id}]   CRUD; namespace, name, version, title, summary come from the
 *                                                       YAML's document block (the YAML is the only input)
 * POST /api/workflowdefinitions/validate                {"yaml"} -> {document, workflow (parsed)}, or 400 + reason
 * POST /api/workflowdefinitions/{id}/deploy             flow-bridge writes the file, waits for the runner to load it
 * GET  /api/workflowdefinitions/deploy-status           whether this snello can deploy (bridge URL + token, writes on)
 * POST /api/workflowdefinitions/import                  {namespace, name, version} of a workflow the runner runs but
 *                                                       that is not here (git-managed copy): saved here as DEPLOYED,
 *                                                       its file becomes the editor's (same version, nothing restarts)
 * </pre>
 * Status: DRAFT (editable) -> DEPLOYED (immutable: the runner 1.3.0 never reloads an edited file, so a change is a
 * new version, and instances already running keep theirs) or FAILED (editable, {@code message} = the runner's error).
 * Deleting a DEPLOYED row removes its file; the runner keeps the workflow loaded until it restarts.
 */
@Path(WORKFLOW_DEFINITIONS_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class WorkflowDefinitionsServiceRs extends AbstractServiceRs {

    public static final String DRAFT = "DRAFT";
    public static final String DEPLOYED = "DEPLOYED";
    public static final String FAILED = "FAILED";

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9-]{1,100}");
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9.+-]{1,50}");

    @Inject
    FlowClient flowClient;

    @Inject
    SecurityIdentity identity;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    WorkflowDefinitionsServiceRs(ApiService apiService) {
        super(apiService, WORKFLOW_DEFINITIONS, "namespace asc, name asc, version desc");
    }

    public WorkflowDefinitionsServiceRs() {
    }

    @Override
    protected void prePersist(Map<String, Object> map) throws Exception {
        WorkflowDocument wd = WorkflowYamlValidator.validate(str(map.get("yaml")));
        requireUnique(wd, null);
        fill(map, wd);
        map.put("status", DRAFT);
        map.put("message", null);
        map.put("created_at", now());
        map.put("created_by", user());
        map.put("deployed_at", null);
        map.put("deployed_by", null);
        Log.infof("workflow definition create %s by %s", wd.id(), user());
        super.prePersist(map);
    }

    @Override
    protected void preUpdate(String id, Map<String, Object> map) throws Exception {
        Map<String, Object> stored = load(id);
        if (DEPLOYED.equals(stored.get("status"))) {
            Log.infof("workflow definition update %s refused: %s is DEPLOYED", id, idOf(stored));
            throw conflict(idOf(stored) + " is deployed and cannot change: save it as a new version");
        }
        WorkflowDocument wd = WorkflowYamlValidator.validate(str(map.get("yaml")));
        requireUnique(wd, id);
        // only the YAML is input: every other column is derived or kept
        map.keySet().retainAll(List.of("yaml"));
        fill(map, wd);
        map.put("status", DRAFT);
        map.put("message", null);
        Log.infof("workflow definition update %s (%s, was %s) by %s", id, wd.id(), idOf(stored), user());
    }

    @POST
    @Path("/validate")
    @RolesAllowed({"Admin", "admin", "Manager", "manager"})
    public Response validate(Map<String, Object> body) {
        return Response.ok(WorkflowYamlValidator.parse(body == null ? null : str(body.get("yaml")))).build();
    }

    @GET
    @Path("/deploy-status")
    @RolesAllowed({"Admin", "admin", "Manager", "manager"})
    public Response deployStatus() {
        FlowProxyResponse r = flowClient.bridge("GET", "/workflows");
        Log.infof("workflow deploy status: bridge -> %d", r.status());
        return Response.status(r.status()).entity(r.body()).type(MediaType.APPLICATION_JSON).build();
    }

    @POST
    @Path("/{id}/deploy")
    @RolesAllowed({"Admin", "admin", "Manager", "manager"})
    public Response deploy(@PathParam("id") String id) throws Exception {
        Map<String, Object> row = load(id);
        String yaml = str(row.get("yaml"));
        if (DEPLOYED.equals(row.get("status"))) {
            throw conflict(idOf(row) + " is already deployed");
        }
        WorkflowDocument wd = WorkflowYamlValidator.validate(yaml);
        Log.infof("workflow deploy %s (%s) by %s", wd.id(), id, user());
        FlowProxyResponse r = flowClient.bridgeWorkflow("PUT", wd.namespace(), wd.name(), wd.version(), yaml);
        Map<String, Object> upd = new HashMap<>();
        if (r.status() == 201) {
            Object secs = json(r.body()).get("seconds");
            upd.put("status", DEPLOYED);
            upd.put("deployed_at", now());
            upd.put("deployed_by", user());
            upd.put("message", "loaded by the runner" + (secs == null ? "" : " in " + secs + " s"));
            Log.infof("workflow deploy %s: DEPLOYED (%s s)", wd.id(), secs);
            apiService.merge(WORKFLOW_DEFINITIONS, upd, id, UUID);
            return Response.ok(load(id)).build();
        }
        Object err = json(r.body()).get("error");
        String error = err == null ? null : err.toString();
        if (error == null) {
            error = "flow-bridge answered " + r.status();
        }
        // 400/409/422: the workflow itself was refused -> FAILED; 5xx: bridge/runner trouble, the workflow keeps its status
        boolean refused = r.status() == 400 || r.status() == 409 || r.status() == 422;
        if (refused) {
            upd.put("status", FAILED);
        }
        upd.put("message", error.length() > 2000 ? error.substring(0, 2000) : error);
        apiService.merge(WORKFLOW_DEFINITIONS, upd, id, UUID);
        Log.warnf("workflow deploy %s: bridge %d -> %s: %s", wd.id(), r.status(), refused ? FAILED : "unchanged", error);
        throw new WebApplicationException(Response.status(refused ? 422 : r.status())
                .type(MediaType.APPLICATION_JSON).entity(Map.of("msg", error)).build());
    }

    /**
     * Brings a workflow the runner runs from a file snello did not write (the git-managed copies) into the editor, so
     * every workflow is managed here. Same namespace:name:version, so instances already running are untouched:
     * flow-bridge copies the file to ui-*.yaml (the runner skips it until its next start, then loads it from there).
     * The YAML is the original file when the bridge still has it (comments kept), else rebuilt from the runner.
     */
    @POST
    @Path("/import")
    @RolesAllowed({"Admin", "admin", "Manager", "manager"})
    public Response importFromRunner(Map<String, Object> body) throws Exception {
        String ns = body == null ? null : str(body.get("namespace"));
        String name = body == null ? null : str(body.get("name"));
        String version = body == null ? null : str(body.get("version"));
        Log.infof("workflow import %s:%s:%s requested by %s", ns, name, version, user());
        if (ns == null || name == null || version == null || !NAME.matcher(ns).matches()
                || !NAME.matcher(name).matches() || !VERSION.matcher(version).matches()) {
            throw new WebApplicationException(Response.status(400).type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("msg", "namespace, name and version are required")).build());
        }
        String yaml;
        String source;
        FlowProxyResponse dry = flowClient.bridgeAdopt(ns, name, version, null, true);
        if (dry.status() == 200) {
            yaml = str(json(dry.body()).get("yaml"));
            source = str(json(dry.body()).get("source"));
            Log.infof("workflow import %s:%s:%s: YAML from file %s (%d chars)", ns, name, version, source,
                    yaml == null ? 0 : yaml.length());
        } else if (dry.status() == 404 && "no-source".equals(json(dry.body()).get("code"))) {
            // e.g. deleted here (file removed) but still loaded until the runner restarts: rebuild it from the runner
            FlowProxyResponse r = flowClient.runner("GET", "/q/flow/definitions/" + ns + "/" + name + "/" + version);
            if (r.status() != 200) {
                throw bridgeError("the runner answered " + r.status(), r.status() == 404 ? 404 : 502);
            }
            yaml = RunnerWorkflowYaml.fromRunnerJson(r.body());
            source = null;
            Log.infof("workflow import %s:%s:%s: no file on the runner, YAML rebuilt (%d chars)", ns, name, version,
                    yaml.length());
        } else {
            Object err = json(dry.body()).get("error");
            Log.warnf("workflow import %s:%s:%s: bridge %d %s", ns, name, version, dry.status(), err);
            throw bridgeError(err == null ? "flow-bridge answered " + dry.status() : err.toString(), dry.status());
        }
        WorkflowDocument wd = WorkflowYamlValidator.validate(yaml);
        if (!ns.equals(wd.namespace()) || !name.equals(wd.name()) || !version.equals(wd.version())) {
            Log.warnf("workflow import %s:%s:%s: YAML says %s", ns, name, version, wd.id());
            throw bridgeError("the workflow file says " + wd.id(), 409);
        }
        requireUnique(wd, null);
        FlowProxyResponse adopted = flowClient.bridgeAdopt(ns, name, version, yaml, false);
        if (adopted.status() != 200) {
            Object err = json(adopted.body()).get("error");
            Log.warnf("workflow import %s: adopt -> %d %s", wd.id(), adopted.status(), err);
            throw bridgeError(err == null ? "flow-bridge answered " + adopted.status() : err.toString(), adopted.status());
        }
        Map<?, ?> res = json(adopted.body());
        Map<String, Object> row = new HashMap<>();
        row.put(UUID, java.util.UUID.randomUUID().toString());
        row.put("yaml", yaml);
        fill(row, wd);
        Timestamp now = now();
        row.put("status", DEPLOYED);
        row.put("message", "imported from the runner" + (source == null ? "" : " (file " + source + ")")
                + (res.get("removed") instanceof List<?> l && !l.isEmpty() ? ", git copy removed" : ""));
        row.put("created_at", now);
        row.put("created_by", user());
        row.put("deployed_at", now);
        row.put("deployed_by", user());
        Map<String, Object> saved = apiService.create(WORKFLOW_DEFINITIONS, row, UUID);
        Log.infof("workflow import %s: saved as %s DEPLOYED, file %s, removed %s", wd.id(), row.get(UUID),
                res.get("file"), res.get("removed"));
        return Response.ok(saved).build();
    }

    private static WebApplicationException bridgeError(String msg, int status) {
        int code = status == 404 || status == 409 || status == 400 ? status : status >= 500 ? status : 502;
        return new WebApplicationException(Response.status(code).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("msg", msg)).build());
    }

    /**
     * Reads are Admin / Manager too (the HTTP policy permits every GET on /api/*): a workflow names internal hosts,
     * Keycloak clients and the runner's secret keys.
     */
    @Override
    @GET
    @RolesAllowed({"Admin", "admin", "Manager", "manager"})
    public Response list(@QueryParam(SORT_PARAM) String sort, @QueryParam(LIMIT_PARAM) String limit,
                         @QueryParam(START_PARAM) String start, @Context UriInfo uriInfo) throws Exception {
        return super.list(sort, limit, start, uriInfo);
    }

    /**
     * Answers the whole stored row: preUpdate trims the input to the YAML-derived columns, and the base class would
     * answer that partial map (no uuid / status / deployed_*), which the admin then takes as the element.
     */
    @Override
    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") String id, Map<String, Object> map) throws Exception {
        super.update(id, map);
        return Response.ok(load(id)).build();
    }

    @Override
    @GET
    @Path("/{id}")
    @RolesAllowed({"Admin", "admin", "Manager", "manager"})
    public Response fetch(@PathParam("id") String id) throws Exception {
        return super.fetch(id);
    }

    @Override
    @DELETE
    @Path("/{id}")
    @RolesAllowed({"Admin", "admin", "Manager", "manager"})
    public Response delete(@PathParam("id") @NotNull String id) throws Exception {
        Map<String, Object> row = load(id);
        if (DEPLOYED.equals(row.get("status"))) {
            FlowProxyResponse r = flowClient.bridgeWorkflow("DELETE", str(row.get("namespace")), str(row.get("name")),
                    str(row.get("version")), null);
            Log.infof("workflow delete %s: bridge removed file -> %d %s", idOf(row), r.status(), r.body());
            if (r.status() >= 300) {
                throw new WebApplicationException(Response.status(r.status()).type(MediaType.APPLICATION_JSON)
                        .entity(Map.of("msg", "the workflow file could not be removed: "
                                + json(r.body()).get("error"))).build());
            }
        }
        Log.infof("workflow delete %s (%s, %s) by %s", id, idOf(row), row.get("status"), user());
        return super.delete(id);
    }

    private Map<String, Object> load(String id) throws Exception {
        Map<String, Object> row = apiService.fetch(null, WORKFLOW_DEFINITIONS, id, UUID);
        if (row == null || row.isEmpty()) {
            throw new NotFoundException("workflow definition " + id + " not found");
        }
        return row;
    }

    /** One row per namespace:name:version: the runner keys workflows by it. */
    private void requireUnique(WorkflowDocument wd, String selfId) throws Exception {
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add("namespace", wd.namespace());
        params.add("name", wd.name());
        params.add("version", wd.version());
        List<Map<String, Object>> same = apiService.list(WORKFLOW_DEFINITIONS, params, "version asc", 10, 0);
        boolean taken = same.stream().anyMatch(r -> wd.namespace().equals(r.get("namespace"))
                && wd.name().equals(r.get("name")) && wd.version().equals(r.get("version"))
                && !String.valueOf(r.get(UUID)).equals(selfId));
        if (taken) {
            Log.infof("workflow definition %s already exists -> 409", wd.id());
            throw conflict(wd.id() + " already exists: change document.version");
        }
    }

    private static void fill(Map<String, Object> map, WorkflowDocument wd) {
        map.put("namespace", wd.namespace());
        map.put("name", wd.name());
        map.put("version", wd.version());
        map.put("title", cut(wd.title(), 255));
        map.put("summary", cut(wd.summary(), 1000));
    }

    private String user() {
        return identity == null || identity.isAnonymous() ? null : identity.getPrincipal().getName();
    }

    private static String idOf(Map<String, Object> row) {
        return row.get("namespace") + ":" + row.get("name") + ":" + row.get("version");
    }

    private static WebApplicationException conflict(String msg) {
        return new WebApplicationException(Response.status(Response.Status.CONFLICT)
                .type(MediaType.APPLICATION_JSON).entity(Map.of("msg", msg)).build());
    }

    /** The bridge's JSON answer as a map; empty if it is not JSON (e.g. a proxy error page). */
    private Map<?, ?> json(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(body, Map.class);
        } catch (Exception e) {
            Log.warnf("flow-bridge answer is not JSON: %s", body.length() > 200 ? body.substring(0, 200) : body);
            return Map.of();
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static Timestamp now() {
        return Timestamp.from(Instant.now());
    }
}
