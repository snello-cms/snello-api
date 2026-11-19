package io.snello.service;

import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import io.snello.service.flow.FlowClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.snello.management.AppConstants.HUMAN_TASKS;
import static io.snello.management.AppConstants.PUBLIC_PAGES_PATH;

/**
 * Human tasks: a decision a workflow waits for.
 * <pre>
 * OPEN      created by the workflow (Admin / Manager, e.g. its service account)
 *           decide(decision, comment): a viewer with one of the task's roles; decision must be one of `decisions`;
 *           undo: clears it again, as long as the task is OPEN (the workflow applies a decision only if it is still
 *           unchanged after its grace period: that is the UNDO window)
 * CLOSED    the workflow applied the decision; no more changes
 * CANCELLED the workflow (or an admin) dropped it; also set here when the task's workflow instance ended without
 *           closing it (cancelled / faulted / completed): nobody can act on that decision any more
 * </pre>
 * While the instance is SUSPENDED the task stays OPEN but decide / undo answer 409 ("on hold") until it is resumed.
 * The instance status comes from flow-bridge; when it is unknown (no instance, bridge down or not tracking it) the
 * task rules alone apply, so a bridge outage never blocks a decision.
 * The page decides nothing: every rule is checked here, so a modified page cannot approve what its user may not.
 */
@ApplicationScoped
public class HumanTaskService {

    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";
    public static final String CANCELLED = "CANCELLED";
    /** instance statuses after which an OPEN task can no longer be acted on by its workflow */
    private static final Set<String> WORKFLOW_ENDED = Set.of("CANCELLED", "FAULTED", "COMPLETED");
    private static final String WORKFLOW_SUSPENDED = "SUSPENDED";
    private static final Set<String> MANAGERS = Set.of("Admin", "admin", "Manager", "manager");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private static final Pattern PAGE = Pattern.compile("[a-z0-9][a-z0-9-]{0,99}");
    /** fields a workflow may set when creating a task; everything else is managed here */
    private static final Set<String> CREATE_FIELDS = Set.of("title", "description", "page_name", "workflow_namespace",
            "workflow_name", "workflow_version", "instance_id", "record_table", "record_key", "decisions", "roles",
            "data");

    @Inject
    ApiService apiService;

    @Inject
    FlowClient flowClient;

    // Optional, not defaultValue = "": MicroProfile Config treats an empty default as missing (startup failure).
    // Unset = relative task links (/p/...).
    @ConfigProperty(name = "snello.public.url")
    Optional<String> publicUrl;

    private final JsonMapper json = JsonMapper.builder().build();

    public Map<String, Object> create(Map<String, Object> body, SecurityIdentity who) throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        body.forEach((k, v) -> {
            if (CREATE_FIELDS.contains(k) && v != null) {
                row.put(k, v);
            }
        });
        Object title = row.get("title");
        if (title == null || title.toString().isBlank()) {
            throw new BadRequestException("title is required");
        }
        row.put("decisions", csv("decisions", row.getOrDefault("decisions", "APPROVE,REJECT")));
        row.put("roles", csv("roles", row.getOrDefault("roles", "Admin,Manager")));
        if (row.get("page_name") != null && !PAGE.matcher(row.get("page_name").toString()).matches()) {
            throw new BadRequestException("page_name: lower case letters, digits and '-'");
        }
        if (row.get("record_table") != null && apiService.metadata(row.get("record_table").toString()) == null) {
            throw new BadRequestException("record_table: unknown table " + row.get("record_table"));
        }
        if (row.get("data") != null && !(row.get("data") instanceof String)) {
            row.put("data", json.writeValueAsString(row.get("data")));
        }
        row.put("uuid", java.util.UUID.randomUUID().toString());
        row.put("status", OPEN);
        row.put("created_at", now());
        Map<String, Object> created = apiService.create(HUMAN_TASKS, row, "uuid");
        Log.infof("human task %s created by %s: '%s' workflow=%s:%s instance=%s record=%s/%s decisions=%s roles=%s page=%s",
                row.get("uuid"), name(who), title, row.get("workflow_namespace"), row.get("workflow_name"),
                row.get("instance_id"), row.get("record_table"), row.get("record_key"), row.get("decisions"),
                row.get("roles"), row.get("page_name"));
        return withUrl(created);
    }

    public Map<String, Object> get(String uuid, SecurityIdentity who) throws Exception {
        Map<String, Object> t = load(uuid);
        requireViewer(t, who);
        return withUrl(t);
    }

    /** Filters: status, instance_id, record_table, record_key, workflow_name (exact). Viewers see their tasks only. */
    public List<Map<String, Object>> list(Map<String, String> filters, int limit, SecurityIdentity who) throws Exception {
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        for (String k : List.of("status", "instance_id", "record_table", "record_key", "workflow_name", "workflow_namespace")) {
            String v = filters.get(k);
            if (v != null && !v.isBlank()) {
                params.add(k, v);
            }
        }
        List<Map<String, Object>> rows = apiService.list(HUMAN_TASKS, params, "created_at desc", Math.max(1, Math.min(limit, 500)), 0);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            if (isViewer(r, who)) {
                out.add(withUrl(r));
            }
        }
        Log.infof("human tasks list %s by %s -> %d of %d", params, name(who), out.size(), rows.size());
        return out;
    }

    public Map<String, Object> decide(String uuid, String decision, String comment, SecurityIdentity who) throws Exception {
        Map<String, Object> t = load(uuid);
        requireViewer(t, who);
        t = requireActionable(t, "decide");
        List<String> allowed = split(t.get("decisions"));
        if (decision == null || !allowed.contains(decision)) {
            Log.infof("human task %s: decision '%s' by %s not in %s -> 400", uuid, decision, name(who), allowed);
            throw new BadRequestException("decision must be one of " + allowed);
        }
        Map<String, Object> upd = new LinkedHashMap<>();
        upd.put("decision", decision);
        upd.put("decision_comment", comment == null ? null : comment.substring(0, Math.min(comment.length(), 4000)));
        upd.put("decided_by", name(who));
        upd.put("decided_at", now());
        Map<String, Object> r = apiService.merge(HUMAN_TASKS, upd, uuid, "uuid");
        Log.infof("human task %s: %s by %s (instance %s)", uuid, decision, name(who), t.get("instance_id"));
        return withUrl(r);
    }

    public Map<String, Object> undo(String uuid, SecurityIdentity who) throws Exception {
        Map<String, Object> t = load(uuid);
        requireViewer(t, who);
        t = requireActionable(t, "undo");
        Map<String, Object> upd = new LinkedHashMap<>();
        upd.put("decision", null);
        upd.put("decision_comment", null);
        upd.put("decided_by", null);
        upd.put("decided_at", null);
        Map<String, Object> r = apiService.merge(HUMAN_TASKS, upd, uuid, "uuid");
        Log.infof("human task %s: decision %s undone by %s", uuid, t.get("decision"), name(who));
        return withUrl(r);
    }

    /** By the workflow once the decision is applied (CLOSED) or when it gives up (CANCELLED). Managers only. */
    public Map<String, Object> close(String uuid, String status, String note, SecurityIdentity who) throws Exception {
        Map<String, Object> t = load(uuid);
        requireOpen(t);
        Map<String, Object> upd = new LinkedHashMap<>();
        upd.put("status", status);
        upd.put("closed_at", now());
        if (note != null && !note.isBlank()) {
            String prev = t.get("decision_comment") == null ? "" : t.get("decision_comment") + "\n";
            upd.put("decision_comment", prev + "[" + status.toLowerCase() + "] " + note);
        }
        Map<String, Object> r = apiService.merge(HUMAN_TASKS, upd, uuid, "uuid");
        Log.infof("human task %s %s by %s (decision %s)", uuid, status, name(who), t.get("decision"));
        return withUrl(r);
    }

    /**
     * Everything a page needs: the task, the record under review (read with the table's field list, for generic
     * pages), the page and the viewer. Personal data leaves snello only here, after the viewer check.
     */
    public Map<String, Object> context(String uuid, SecurityIdentity who) throws Exception {
        Map<String, Object> t = load(uuid);
        requireViewer(t, who);
        Map<String, Object> ctx = new LinkedHashMap<>();
        Map<String, Object> workflow = workflowOf(t);
        if (workflow != null) {
            ctx.put("workflow", workflow);
        }
        String wfStatus = workflow == null ? null : str(workflow.get("status"));
        t = cancelIfWorkflowEnded(t, wfStatus);
        if (OPEN.equals(t.get("status")) && WORKFLOW_SUSPENDED.equals(wfStatus)) {
            // the page shows it and keeps the buttons off; decide/undo refuse it anyway
            ctx.put("hold", "The workflow is suspended: decisions are on hold until it is resumed.");
        }
        ctx.put("task", withUrl(t));
        String table = str(t.get("record_table"));
        String key = str(t.get("record_key"));
        if (table != null && key != null) {
            io.snello.model.Metadata m = apiService.metadataWithFields(table);
            if (m != null) {
                ctx.put("record", apiService.fetch(null, table, key, m.table_key));
                List<Map<String, Object>> fields = new ArrayList<>();
                (m.fields == null ? List.<io.snello.model.FieldDefinition>of() : m.fields).stream()
                        .sorted((a, b) -> Integer.compare(a.order_num, b.order_num))
                        .forEach(f -> {
                            Map<String, Object> fm = new LinkedHashMap<>();
                            fm.put("name", f.name);
                            fm.put("label", f.label);
                            fm.put("type", f.type);
                            fm.put("options", f.options);
                            fields.add(fm);
                        });
                ctx.put("fields", fields);
                ctx.put("recordTitle", m.description);
            }
        }
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("name", name(who));
        user.put("roles", who.getRoles());
        ctx.put("user", user);
        Log.infof("human task %s context for %s: record=%s/%s workflow=%s task=%s%s", uuid, name(who), table, key,
                wfStatus, t.get("status"), ctx.containsKey("hold") ? " (on hold)" : "");
        return ctx;
    }

    public boolean isManager(SecurityIdentity who) {
        return who != null && !who.isAnonymous() && MANAGERS.stream().anyMatch(who::hasRole);
    }

    private boolean isViewer(Map<String, Object> t, SecurityIdentity who) {
        if (who == null || who.isAnonymous()) {
            return false;
        }
        if (isManager(who)) {
            return true;
        }
        return split(t.get("roles")).stream().anyMatch(who::hasRole);
    }

    private void requireViewer(Map<String, Object> t, SecurityIdentity who) {
        if (who == null || who.isAnonymous()) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }
        if (!isViewer(t, who)) {
            Log.infof("human task %s: %s (roles %s) is not in %s -> 403", t.get("uuid"), name(who), who.getRoles(),
                    t.get("roles"));
            throw new ForbiddenException("not one of this task's roles");
        }
    }

    /**
     * Cancels the OPEN tasks of an instance (an admin cancelled it from the monitor): the cancelled workflow can never
     * close them itself. Returns how many were cancelled.
     */
    public int cancelOpenOfInstance(String instanceId, String note, SecurityIdentity who) throws Exception {
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add("instance_id", instanceId);
        params.add("status", OPEN);
        List<Map<String, Object>> open = apiService.list(HUMAN_TASKS, params, null, 100, 0);
        for (Map<String, Object> t : open) {
            close(str(t.get("uuid")), CANCELLED, note, who);
        }
        Log.infof("instance %s: %d open human task(s) cancelled by %s", instanceId, open.size(), name(who));
        return open.size();
    }

    /** The task's instance as flow-bridge sees it, or null when unknown (no instance, bridge down / not tracking it). */
    private Map<String, Object> workflowOf(Map<String, Object> t) {
        Object instance = t.get("instance_id");
        if (instance == null) {
            return null;
        }
        try {
            var r = flowClient.bridge("GET", "/instances/" + instance);
            if (r.status() == 200) {
                @SuppressWarnings("unchecked")
                Map<String, Object> w = json.readValue(r.body(), Map.class);
                return w;
            }
            Log.infof("human task %s: instance %s unknown to flow-bridge (%d): task rules only", t.get("uuid"), instance,
                    r.status());
        } catch (Exception e) {
            Log.warnf("human task %s: flow-bridge not readable for instance %s (%s): task rules only", t.get("uuid"),
                    instance, e.getMessage());
        }
        return null;
    }

    /** An OPEN task whose instance has ended can never be applied: mark it CANCELLED (once) and return it reloaded. */
    private Map<String, Object> cancelIfWorkflowEnded(Map<String, Object> t, String wfStatus) throws Exception {
        if (!OPEN.equals(t.get("status")) || wfStatus == null || !WORKFLOW_ENDED.contains(wfStatus)) {
            return t;
        }
        String uuid = str(t.get("uuid"));
        Log.infof("human task %s: instance %s is %s while the task is OPEN -> CANCELLED", uuid, t.get("instance_id"),
                wfStatus);
        Map<String, Object> upd = new LinkedHashMap<>();
        upd.put("status", CANCELLED);
        upd.put("closed_at", now());
        String prev = t.get("decision_comment") == null ? "" : t.get("decision_comment") + "\n";
        upd.put("decision_comment", prev + "[cancelled] workflow " + wfStatus.toLowerCase() + ": no decision needed any more");
        apiService.merge(HUMAN_TASKS, upd, uuid, "uuid");
        return load(uuid);
    }

    /** decide / undo: task OPEN, its workflow not ended (else the task is cancelled now) and not suspended. */
    private Map<String, Object> requireActionable(Map<String, Object> t, String what) throws Exception {
        if (!OPEN.equals(t.get("status"))) {
            requireOpen(t);
        }
        Map<String, Object> workflow = workflowOf(t);
        String wfStatus = workflow == null ? null : str(workflow.get("status"));
        Map<String, Object> now = cancelIfWorkflowEnded(t, wfStatus);
        if (!OPEN.equals(now.get("status"))) {
            Log.infof("human task %s: %s refused, workflow %s -> 409", t.get("uuid"), what, wfStatus);
            throw conflict("the workflow is " + wfStatus.toLowerCase() + ": this decision is no longer needed");
        }
        if (WORKFLOW_SUSPENDED.equals(wfStatus)) {
            Log.infof("human task %s: %s refused, workflow suspended -> 409", t.get("uuid"), what);
            throw conflict("the workflow is suspended: decisions are on hold until it is resumed");
        }
        return now;
    }

    private static WebApplicationException conflict(String msg) {
        return new WebApplicationException(Response.status(Response.Status.CONFLICT).entity(Map.of("msg", msg)).build());
    }

    private static void requireOpen(Map<String, Object> t) {
        if (!OPEN.equals(t.get("status"))) {
            Log.infof("human task %s is %s -> 409", t.get("uuid"), t.get("status"));
            throw new WebApplicationException(Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("msg", "task is " + t.get("status"))).build());
        }
    }

    private Map<String, Object> load(String uuid) throws Exception {
        if (uuid == null || !TOKEN.matcher(uuid).matches()) {
            throw new BadRequestException("invalid task id");
        }
        Map<String, Object> t = apiService.fetch(null, HUMAN_TASKS, uuid, "uuid");
        if (t == null || t.isEmpty()) {
            throw new NotFoundException("unknown task " + uuid);
        }
        return t;
    }

    private Map<String, Object> withUrl(Map<String, Object> t) {
        Map<String, Object> out = new LinkedHashMap<>(t);
        String page = Optional.ofNullable(str(t.get("page_name"))).orElse("task");
        out.put("url", publicUrl.orElse("").replaceAll("/+$", "") + PUBLIC_PAGES_PATH + "/" + page + "?task=" + t.get("uuid"));
        return out;
    }

    private static String csv(String what, Object value) {
        List<String> items = split(value);
        if (items.isEmpty() || items.stream().anyMatch(s -> !TOKEN.matcher(s).matches())) {
            throw new BadRequestException(what + ": comma separated names (letters, digits, _ and -)");
        }
        return String.join(",", items);
    }

    private static List<String> split(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> l) {
            return l.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        }
        return Arrays.stream(value.toString().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String str(Object o) {
        return o == null || o.toString().isBlank() ? null : o.toString();
    }

    private static String name(SecurityIdentity who) {
        return who == null || who.isAnonymous() ? "anonymous" : who.getPrincipal().getName();
    }

    private static Timestamp now() {
        return Timestamp.from(Instant.now());
    }
}
