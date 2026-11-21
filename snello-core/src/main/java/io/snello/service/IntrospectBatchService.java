package io.snello.service;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.snello.api.service.JdbcRepository;
import io.snello.management.SnelloPlane;
import io.snello.model.pojo.IntrospectBatchRequest;
import io.snello.model.pojo.IntrospectJob;
import io.snello.model.pojo.IntrospectJobLog;
import io.snello.model.pojo.IntrospectRequest;
import io.snello.model.pojo.IntrospectResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.snello.management.AppConstants.INTROSPECT_JOBS;
import static io.snello.management.AppConstants.INTROSPECT_JOB_LOGS;
import static io.snello.management.AppConstants.UUID;
import static io.snello.service.IntrospectService.MAX_TABLES_PER_REQUEST;

/**
 * Async auto-apply of remaining user tables in chunks of {@link IntrospectService#MAX_TABLES_PER_REQUEST}.
 * One concurrent batch per JVM; jobs and logs are persisted for SPA polling.
 */
@ApplicationScoped
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class IntrospectBatchService {

    public static final String STATUS_QUEUED = "queued";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_CANCELLED = "cancelled";

    private static final int LIST_LIMIT = 50;
    private static final int WARN_TRUNCATE = 400;

    @Inject
    JdbcRepository jdbcRepository;

    @Inject
    IntrospectService introspectService;

    /** In-memory gate: only one worker runs on this node. */
    private final AtomicBoolean workerBusy = new AtomicBoolean(false);

    void onStart(@Observes StartupEvent event) {
        try {
            failInterruptedJobs();
        } catch (Exception e) {
            Log.warnf(e, "introspect batch: could not mark interrupted jobs failed");
        }
    }

    /**
     * Enqueue a batch over all non-reserved, not-yet-referenced tables. Returns immediately with 202 payload.
     *
     * @throws ConflictException when another job is already queued/running
     */
    public IntrospectJob start(IntrospectBatchRequest request, String createdBy) throws Exception {
        if (hasActiveJob()) {
            throw new ConflictException("an introspect batch is already queued or running");
        }
        IntrospectBatchRequest req = request == null ? new IntrospectBatchRequest(null, null) : request;
        List<String> tables = introspectService.availableTables();
        String now = Instant.now().toString();
        String jobUuid = java.util.UUID.randomUUID().toString();
        Map<String, Object> row = new HashMap<>();
        row.put(UUID, jobUuid);
        row.put("status", tables.isEmpty() ? STATUS_COMPLETED : STATUS_QUEUED);
        row.put("created_at", now);
        row.put("started_at", tables.isEmpty() ? now : null);
        row.put("finished_at", tables.isEmpty() ? now : null);
        row.put("created_by", createdBy);
        row.put("metadata_group", req.metadata_group());
        row.put("sample_rows", req.sampleRowsOrDefault());
        row.put("total_tables", tables.size());
        row.put("done_tables", 0);
        row.put("failed_tables", 0);
        row.put("current_table", null);
        row.put("error_message", null);
        jdbcRepository.create(INTROSPECT_JOBS, UUID, row);
        appendLog(jobUuid, "info", null,
                tables.isEmpty() ? "no available tables to import" : "enqueued " + tables.size() + " tables");
        if (!tables.isEmpty()) {
            schedule(jobUuid, tables, req.sampleRowsOrDefault(), req.metadata_group());
        }
        return toJob(row);
    }

    public List<IntrospectJob> listJobs() throws Exception {
        List<Map<String, Object>> rows = jdbcRepository.list(INTROSPECT_JOBS, "created_at desc");
        List<IntrospectJob> out = new ArrayList<>();
        int n = 0;
        for (Map<String, Object> row : rows) {
            out.add(toJob(row));
            if (++n >= LIST_LIMIT) {
                break;
            }
        }
        return out;
    }

    public IntrospectJob getJob(String uuid) throws Exception {
        Map<String, Object> row = jdbcRepository.fetch(null, INTROSPECT_JOBS, UUID, uuid);
        return row == null || row.isEmpty() ? null : toJob(row);
    }

    public List<IntrospectJobLog> logs(String jobUuid, String after) throws Exception {
        List<Map<String, Object>> rows = jdbcRepository.list(INTROSPECT_JOB_LOGS, "created_at asc");
        List<IntrospectJobLog> out = new ArrayList<>();
        boolean skip = after != null && !after.isBlank();
        for (Map<String, Object> row : rows) {
            if (!jobUuid.equals(String.valueOf(row.get("job_uuid")))) {
                continue;
            }
            String id = String.valueOf(row.get(UUID));
            if (skip) {
                if (after.equals(id)) {
                    skip = false;
                }
                continue;
            }
            out.add(toLog(row));
        }
        return out;
    }

    /**
     * Best-effort cancel between chunks. Marks the job cancelled; the worker exits at the next chunk boundary.
     */
    public IntrospectJob cancel(String uuid) throws Exception {
        Map<String, Object> row = jdbcRepository.fetch(null, INTROSPECT_JOBS, UUID, uuid);
        if (row == null || row.isEmpty()) {
            return null;
        }
        String status = str(row.get("status"));
        if (!STATUS_QUEUED.equals(status) && !STATUS_RUNNING.equals(status)) {
            return toJob(row);
        }
        row.put("status", STATUS_CANCELLED);
        row.put("finished_at", Instant.now().toString());
        row.put("current_table", null);
        jdbcRepository.update(INTROSPECT_JOBS, UUID, patch(row, "status", "finished_at", "current_table"), uuid);
        appendLog(uuid, "warn", null, "cancel requested");
        return getJob(uuid);
    }

    private void schedule(String jobUuid, List<String> tables, int sampleRows, String metadataGroup) {
        ClassLoader callerLoader = Thread.currentThread().getContextClassLoader();
        CompletableFuture.runAsync(() -> {
            Thread worker = Thread.currentThread();
            ClassLoader workerLoader = worker.getContextClassLoader();
            worker.setContextClassLoader(callerLoader);
            try {
                runJob(jobUuid, tables, sampleRows, metadataGroup);
            } catch (Exception e) {
                Log.errorf(e, "introspect batch %s failed", jobUuid);
                try {
                    failJob(jobUuid, e.getMessage());
                } catch (Exception nested) {
                    Log.errorf(nested, "introspect batch %s could not persist failure", jobUuid);
                }
            } finally {
                worker.setContextClassLoader(workerLoader);
            }
        });
    }

    private void runJob(String jobUuid, List<String> tables, int sampleRows, String metadataGroup) throws Exception {
        if (!workerBusy.compareAndSet(false, true)) {
            failJob(jobUuid, "another batch worker is already running on this node");
            return;
        }
        try {
            String started = Instant.now().toString();
            updateFields(jobUuid, Map.of("status", STATUS_RUNNING, "started_at", started));
            appendLog(jobUuid, "info", null, "started");

            int done = 0;
            int failed = 0;
            for (int i = 0; i < tables.size(); i += MAX_TABLES_PER_REQUEST) {
                if (isCancelled(jobUuid)) {
                    appendLog(jobUuid, "warn", null, "stopped after cancel");
                    return;
                }
                List<String> chunk = tables.subList(i, Math.min(i + MAX_TABLES_PER_REQUEST, tables.size()));
                for (String table : chunk) {
                    if (isCancelled(jobUuid)) {
                        appendLog(jobUuid, "warn", null, "stopped after cancel");
                        return;
                    }
                    updateFields(jobUuid, Map.of("current_table", table));
                    appendLog(jobUuid, "info", table, "start table " + table);
                    try {
                        IntrospectResult result = introspectService.apply(
                                new IntrospectRequest(List.of(table), sampleRows, metadataGroup));
                        if (result == null || !result.valid()) {
                            failed++;
                            String err = result == null || result.errors() == null || result.errors().isEmpty()
                                    ? "apply failed"
                                    : String.join("; ", result.errors());
                            appendLog(jobUuid, "error", table, truncate(err));
                        } else if (result.entities() == null || result.entities().isEmpty()) {
                            appendLog(jobUuid, "warn", table, "skipped (already referenced or empty)");
                            done++;
                        } else {
                            done++;
                            appendLog(jobUuid, "info", table, "applied");
                            if (result.warnings() != null) {
                                for (String w : result.warnings()) {
                                    if (w != null && !w.isBlank()) {
                                        appendLog(jobUuid, "warn", table, truncate(w));
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        failed++;
                        appendLog(jobUuid, "error", table, truncate(e.getMessage() == null ? e.toString() : e.getMessage()));
                        Log.warnf(e, "introspect batch %s table %s", jobUuid, table);
                    }
                    updateFields(jobUuid, Map.of(
                            "done_tables", done,
                            "failed_tables", failed,
                            "current_table", table));
                }
            }
            if (isCancelled(jobUuid)) {
                return;
            }
            String finished = Instant.now().toString();
            String finalStatus = failed > 0 && done == 0 ? STATUS_FAILED : STATUS_COMPLETED;
            updateFields(jobUuid, Map.of(
                    "status", finalStatus,
                    "finished_at", finished,
                    "current_table", "",
                    "done_tables", done,
                    "failed_tables", failed,
                    "error_message", failed > 0 ? failed + " table(s) failed" : ""));
            appendLog(jobUuid, failed > 0 ? "warn" : "info", null,
                    "finished: done=" + done + " failed=" + failed);
        } finally {
            workerBusy.set(false);
        }
    }

    private boolean isCancelled(String jobUuid) throws Exception {
        IntrospectJob job = getJob(jobUuid);
        return job != null && STATUS_CANCELLED.equals(job.status());
    }

    private void failJob(String jobUuid, String message) throws Exception {
        String finished = Instant.now().toString();
        updateFields(jobUuid, Map.of(
                "status", STATUS_FAILED,
                "finished_at", finished,
                "current_table", "",
                "error_message", message == null ? "failed" : truncate(message)));
        appendLog(jobUuid, "error", null, truncate(message == null ? "failed" : message));
    }

    private void failInterruptedJobs() throws Exception {
        List<Map<String, Object>> rows = jdbcRepository.list(INTROSPECT_JOBS, "created_at desc");
        String now = Instant.now().toString();
        for (Map<String, Object> row : rows) {
            String status = str(row.get("status"));
            if (!STATUS_RUNNING.equals(status) && !STATUS_QUEUED.equals(status)) {
                continue;
            }
            String uuid = str(row.get(UUID));
            updateFields(uuid, Map.of(
                    "status", STATUS_FAILED,
                    "finished_at", now,
                    "current_table", "",
                    "error_message", "interrupted by restart"));
            appendLog(uuid, "error", null, "interrupted by restart");
        }
    }

    private boolean hasActiveJob() throws Exception {
        if (workerBusy.get()) {
            return true;
        }
        List<Map<String, Object>> rows = jdbcRepository.list(INTROSPECT_JOBS, "created_at desc");
        for (Map<String, Object> row : rows) {
            String status = str(row.get("status"));
            if (STATUS_QUEUED.equals(status) || STATUS_RUNNING.equals(status)) {
                return true;
            }
        }
        return false;
    }

    private void updateFields(String uuid, Map<String, Object> fields) throws Exception {
        Map<String, Object> patch = new HashMap<>(fields);
        // empty string clears current_table for dialects that dislike null in update maps inconsistently
        jdbcRepository.update(INTROSPECT_JOBS, UUID, patch, uuid);
    }

    private void appendLog(String jobUuid, String level, String tableName, String message) throws Exception {
        Map<String, Object> row = new HashMap<>();
        row.put(UUID, java.util.UUID.randomUUID().toString());
        row.put("job_uuid", jobUuid);
        row.put("created_at", Instant.now().toString());
        row.put("level", level);
        row.put("table_name", tableName);
        row.put("message", message == null ? "" : message);
        jdbcRepository.create(INTROSPECT_JOB_LOGS, UUID, row);
    }

    private static Map<String, Object> patch(Map<String, Object> row, String... keys) {
        Map<String, Object> out = new HashMap<>();
        for (String key : keys) {
            out.put(key, row.get(key));
        }
        return out;
    }

    private static IntrospectJob toJob(Map<String, Object> row) {
        return new IntrospectJob(
                str(row.get(UUID)),
                str(row.get("status")),
                str(row.get("created_at")),
                str(row.get("started_at")),
                str(row.get("finished_at")),
                str(row.get("created_by")),
                str(row.get("metadata_group")),
                toInt(row.get("sample_rows")),
                toInt(row.get("total_tables")),
                toInt(row.get("done_tables")),
                toInt(row.get("failed_tables")),
                emptyToNull(str(row.get("current_table"))),
                emptyToNull(str(row.get("error_message"))));
    }

    private static IntrospectJobLog toLog(Map<String, Object> row) {
        return new IntrospectJobLog(
                str(row.get(UUID)),
                str(row.get("job_uuid")),
                str(row.get("created_at")),
                str(row.get("level")),
                emptyToNull(str(row.get("table_name"))),
                str(row.get("message")));
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static int toInt(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= WARN_TRUNCATE ? s : s.substring(0, WARN_TRUNCATE) + "…";
    }

    /** Thrown when a second batch would overlap an active one. */
    public static class ConflictException extends Exception {
        public ConflictException(String message) {
            super(message);
        }
    }
}
