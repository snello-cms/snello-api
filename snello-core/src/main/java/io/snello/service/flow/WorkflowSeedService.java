package io.snello.service.flow;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.snello.management.SnelloPlane;
import io.snello.model.pojo.FlowProxyResponse;
import io.snello.model.pojo.WorkflowDocument;
import io.snello.service.ApiService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static io.snello.management.AppConstants.UUID;
import static io.snello.management.AppConstants.WORKFLOW_DEFINITIONS;

/**
 * Every workflow lives in the admin's Workflows editor: at startup the workflows shipped with snello-flows (the
 * {@code snello.flow.seed-dir} folder, in k8s a ConfigMap rendered from {@code snello-flows/workflows}) that the
 * editor does not have yet are imported into it. "New" = namespace:name:version not in {@code workflowdefinitions}:
 * an existing row is never touched, so an edit in the editor (always a new version) wins over git.
 * <ul>
 *   <li>the runner already runs that version (an old git copy in its folder): flow-bridge adopts it, i.e. the YAML
 *   becomes the editor's ui-*.yaml and the old copy is removed; nothing restarts;</li>
 *   <li>otherwise it is deployed like the editor's Deploy (the bridge waits for the runner): DEPLOYED, or FAILED with
 *   the runner's reason, editable in the editor.</li>
 * </ul>
 * Runs in the background and waits (up to {@code seed-wait-minutes}) for flow-bridge, which may start after snello.
 * Files named ui-*.yaml are skipped: they are what the bridge writes for the editor, not seeds.
 */
@ApplicationScoped
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class WorkflowSeedService {

    static final String SEEDED_BY = "snello-flows";

    @ConfigProperty(name = "snello.flow.seed-dir")
    Optional<String> seedDir;

    @ConfigProperty(name = "snello.flow.seed-wait-minutes", defaultValue = "10")
    int waitMinutes;

    @Inject
    FlowClient flowClient;

    @Inject
    ApiService apiService;

    @Inject
    ObjectMapper objectMapper;

    void onStart(@Observes StartupEvent event) {
        String dir = seedDir.map(String::trim).filter(s -> !s.isEmpty()).orElse(null);
        if (dir == null) {
            Log.info("workflow seed: off (snello.flow.seed-dir not set)");
            return;
        }
        Thread.ofVirtual().name("workflow-seed").start(() -> {
            try {
                run(Path.of(dir));
            } catch (Exception e) {
                Log.errorf(e, "workflow seed from %s failed", dir);
            }
        });
    }

    /** Imports the seed folder's new workflows; returns how many were imported (tests call it directly). */
    public int run(Path dir) throws Exception {
        List<Path> files = seedFiles(dir);
        Log.infof("workflow seed: %d file(s) in %s", files.size(), dir);
        if (files.isEmpty() || !waitForBridge()) {
            return 0;
        }
        int imported = 0;
        for (Path f : files) {
            try {
                if (importFile(f)) {
                    imported++;
                }
            } catch (Exception e) {
                Log.errorf(e, "workflow seed %s: not imported (retried at the next start)", f.getFileName());
            }
        }
        Log.infof("workflow seed: done, %d imported, %d already in the editor or skipped", imported, files.size() - imported);
        return imported;
    }

    private static List<Path> seedFiles(Path dir) throws Exception {
        if (!Files.isDirectory(dir)) {
            Log.warnf("workflow seed: %s is not a folder", dir);
            return List.of();
        }
        try (Stream<Path> s = Files.list(dir)) {
            // ConfigMap mounts hold ..data symlinks and hidden entries: only visible *.yaml / *.yml files
            return s.filter(p -> {
                        String n = p.getFileName().toString();
                        return !n.startsWith(".") && !n.startsWith("ui-") && (n.endsWith(".yaml") || n.endsWith(".yml"))
                                && Files.isRegularFile(p);
                    })
                    .sorted().toList();
        }
    }

    /** flow-bridge answers (GET /workflows 200) before anything is imported: it may start after snello. */
    private boolean waitForBridge() throws InterruptedException {
        long until = System.currentTimeMillis() + waitMinutes * 60_000L;
        int attempt = 0;
        while (true) {
            attempt++;
            FlowProxyResponse r = flowClient.bridge("GET", "/workflows");
            if (r.status() == 200) {
                Log.infof("workflow seed: flow-bridge ready (attempt %d)", attempt);
                return true;
            }
            if (r.status() == 503 && r.body() != null && r.body().contains("not configured")) {
                Log.warnf("workflow seed: flow-bridge not configured on this snello, nothing imported: %s", r.body());
                return false;
            }
            if (System.currentTimeMillis() > until) {
                Log.warnf("workflow seed: flow-bridge still answers %d after %d min, nothing imported (next start retries)",
                        r.status(), waitMinutes);
                return false;
            }
            Log.infof("workflow seed: flow-bridge answers %d, retry in 15 s (attempt %d)", r.status(), attempt);
            Thread.sleep(15_000);
        }
    }

    private boolean importFile(Path f) throws Exception {
        String file = f.getFileName().toString();
        String yaml = Files.readString(f, StandardCharsets.UTF_8);
        WorkflowDocument wd = WorkflowYamlValidator.validate(yaml);
        if (exists(wd)) {
            Log.infof("workflow seed %s: %s already in the editor, skipped", file, wd.id());
            return false;
        }
        String path = wd.namespace() + "/" + wd.name() + "/" + wd.version();
        boolean running = flowClient.runner("GET", "/q/flow/definitions/" + path).status() == 200;
        Log.infof("workflow seed %s: %s new for the editor, runner already runs it: %s", file, wd.id(), running);

        Map<String, Object> row = new HashMap<>();
        Timestamp now = Timestamp.from(Instant.now());
        if (running) {
            FlowProxyResponse r = flowClient.bridgeAdopt(wd.namespace(), wd.name(), wd.version(), yaml, false);
            if (r.status() != 200) {
                Log.warnf("workflow seed %s: adopt %s -> %d %s (retried at the next start)", file, wd.id(), r.status(), r.body());
                return false;
            }
            Object removed = json(r.body()).get("removed");
            row.put("status", "DEPLOYED");
            row.put("message", "imported from snello-flows (" + file + "), already running"
                    + (removed instanceof List<?> l && !l.isEmpty() ? ", old copy " + l + " removed" : ""));
        } else {
            FlowProxyResponse r = flowClient.bridgeWorkflow("PUT", wd.namespace(), wd.name(), wd.version(), yaml);
            if (r.status() == 201) {
                Object secs = json(r.body()).get("seconds");
                row.put("status", "DEPLOYED");
                row.put("message", "imported from snello-flows (" + file + "), loaded by the runner"
                        + (secs == null ? "" : " in " + secs + " s"));
            } else if (r.status() == 400 || r.status() == 409 || r.status() == 422) {
                // refused by the runner: still in the editor (FAILED, editable), with the reason
                Object err = json(r.body()).get("error");
                String msg = "imported from snello-flows (" + file + "), not loaded by the runner: "
                        + (err == null ? "flow-bridge answered " + r.status() : err);
                row.put("status", "FAILED");
                row.put("message", msg.length() > 2000 ? msg.substring(0, 2000) : msg);
            } else {
                Log.warnf("workflow seed %s: deploy %s -> %d %s (retried at the next start)", file, wd.id(), r.status(), r.body());
                return false;
            }
        }
        row.put(UUID, java.util.UUID.randomUUID().toString());
        row.put("yaml", yaml);
        row.put("namespace", wd.namespace());
        row.put("name", wd.name());
        row.put("version", wd.version());
        row.put("title", cut(wd.title(), 255));
        row.put("summary", cut(wd.summary(), 1000));
        row.put("created_at", now);
        row.put("created_by", SEEDED_BY);
        boolean deployed = "DEPLOYED".equals(row.get("status"));
        row.put("deployed_at", deployed ? now : null);
        row.put("deployed_by", deployed ? SEEDED_BY : null);
        apiService.create(WORKFLOW_DEFINITIONS, row, UUID);
        Log.infof("workflow seed %s: %s saved in the editor as %s (%s): %s", file, wd.id(), row.get("status"),
                row.get(UUID), row.get("message"));
        return true;
    }

    private boolean exists(WorkflowDocument wd) throws Exception {
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add("namespace", wd.namespace());
        params.add("name", wd.name());
        params.add("version", wd.version());
        return apiService.list(WORKFLOW_DEFINITIONS, params, "version asc", 10, 0).stream()
                .anyMatch(r -> wd.namespace().equals(r.get("namespace")) && wd.name().equals(r.get("name"))
                        && wd.version().equals(r.get("version")));
    }

    private Map<?, ?> json(String body) {
        try {
            return body == null || body.isBlank() ? Map.of() : objectMapper.readValue(body, Map.class);
        } catch (Exception e) {
            Log.warnf("workflow seed: flow-bridge answer is not JSON: %s", body.length() > 200 ? body.substring(0, 200) : body);
            return Map.of();
        }
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
