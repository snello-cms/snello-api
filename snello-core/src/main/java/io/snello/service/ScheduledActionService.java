package io.snello.service;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import io.quarkus.logging.Log;
import io.snello.api.service.JdbcRepository;
import io.snello.model.ScheduledAction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static io.snello.management.AppConstants.SCHEDULED_ACTIONS;

/**
 * Runs the rows of {@code scheduledactions}.
 * <p>
 * Each enabled row keeps its {@code next_run}. On every tick, rows whose {@code next_run} is past are claimed with a
 * conditional UPDATE ({@code ... WHERE next_run = <old>}), so with several API instances on one database a run
 * happens once. Runs missed while the server was down collapse into a single run on the next tick.
 */
@ApplicationScoped
public class ScheduledActionService {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_OK = "OK";
    public static final String STATUS_ERROR = "ERROR";
    private static final int MAX_MESSAGE = 4000;

    private static final CronParser UNIX = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
    private static final CronParser QUARTZ = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ));

    @Inject
    JdbcRepository jdbcRepository;

    @Inject
    ScriptService scriptService;

    /** Zone the cron expressions are read in, e.g. {@code Europe/Rome}. Defaults to the server zone. */
    @ConfigProperty(name = "snello.scheduled-actions.timezone")
    Optional<String> timezone;

    /** Jobs running in this JVM: a slow job is not started again while it is still running. */
    private final Set<String> running = ConcurrentHashMap.newKeySet();

    public ZoneId zone() {
        return timezone.filter(z -> !z.isBlank()).map(ZoneId::of).orElse(ZoneId.systemDefault());
    }

    /**
     * Parses a 5-field (unix) or 6/7-field (Quartz, seconds first) cron.
     *
     * @throws IllegalArgumentException with a readable message when the expression is invalid
     */
    public static Cron parse(String frequency) {
        if (frequency == null || frequency.isBlank()) {
            throw new IllegalArgumentException("frequency (cron expression) is required");
        }
        String expr = frequency.trim().replaceAll("\\s+", " ");
        int fields = expr.split(" ").length;
        try {
            Cron cron = fields == 5 ? UNIX.parse(expr) : QUARTZ.parse(expr);
            return cron.validate();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid cron '" + frequency + "' (" + fields + " fields): " + e.getMessage(), e);
        }
    }

    /** Next fire time strictly after {@code after}, or null when the cron never fires again. */
    public OffsetDateTime nextRun(String frequency, ZonedDateTime after) {
        Optional<ZonedDateTime> next = ExecutionTime.forCron(parse(frequency)).nextExecution(after);
        return next.map(ZonedDateTime::toOffsetDateTime).orElse(null);
    }

    public String nextRunFromNow(String frequency) {
        OffsetDateTime next = nextRun(frequency, ZonedDateTime.now(zone()));
        return next == null ? null : next.toString();
    }

    public List<ScheduledAction> listAll() throws Exception {
        List<Map<String, Object>> rows = jdbcRepository.list(SCHEDULED_ACTIONS, "name asc");
        List<ScheduledAction> jobs = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            jobs.add(new ScheduledAction(row));
        }
        return jobs;
    }

    public ScheduledAction find(String uuid) throws Exception {
        Map<String, Object> row = jdbcRepository.fetch(null, SCHEDULED_ACTIONS, "uuid", uuid);
        return row == null || row.isEmpty() ? null : new ScheduledAction(row);
    }

    /** One tick: runs every enabled job that is due. Returns how many jobs were run. */
    public int runDue() throws Exception {
        ZonedDateTime now = ZonedDateTime.now(zone());
        List<ScheduledAction> jobs = listAll();
        Log.infof("SCHEDULED-ACTIONS tick at %s: %d job(s) defined", now.toOffsetDateTime(), jobs.size());
        int executed = 0;
        for (ScheduledAction job : jobs) {
            if (!job.enabled) {
                Log.debugf("SCHEDULED-ACTIONS [%s] disabled, skip", job.name);
                continue;
            }
            if (runIfDue(job, now)) {
                executed++;
            }
        }
        Log.infof("SCHEDULED-ACTIONS tick done: %d job(s) run", executed);
        return executed;
    }

    /** Claims and runs {@code job} when its next_run has passed. True when it ran (ok or error). */
    private boolean runIfDue(ScheduledAction job, ZonedDateTime now) {
        String next;
        try {
            OffsetDateTime computed = nextRun(job.frequency, now);
            next = computed == null ? null : computed.toString();
        } catch (IllegalArgumentException e) {
            Log.errorf("SCHEDULED-ACTIONS [%s] %s, skip", job.name, e.getMessage());
            return false;
        }
        if (job.next_run == null || job.next_run.isBlank()) {
            // created by import or raw SQL: start counting from now, do not fire immediately
            boolean set = claim(job.uuid, null, next);
            Log.infof("SCHEDULED-ACTIONS [%s] had no next_run, initialised to %s (updated=%s)", job.name, next, set);
            return false;
        }
        OffsetDateTime due;
        try {
            due = OffsetDateTime.parse(job.next_run);
        } catch (Exception e) {
            Log.warnf("SCHEDULED-ACTIONS [%s] unreadable next_run '%s', resetting to %s", job.name, job.next_run, next);
            claim(job.uuid, job.next_run, next);
            return false;
        }
        if (due.isAfter(now.toOffsetDateTime())) {
            Log.debugf("SCHEDULED-ACTIONS [%s] not due, next_run=%s", job.name, job.next_run);
            return false;
        }
        if (!claim(job.uuid, job.next_run, next)) {
            Log.infof("SCHEDULED-ACTIONS [%s] due at %s but claimed by another instance", job.name, job.next_run);
            return false;
        }
        Log.infof("SCHEDULED-ACTIONS [%s] due at %s (cron '%s'), running; next_run -> %s",
                job.name, job.next_run, job.frequency, next);
        execute(job);
        return true;
    }

    /**
     * Runs one job now and records the outcome in {@code last_*}. Used by the tick and by "run now".
     *
     * @return the stored outcome ({@code last_status}, {@code last_message}, {@code last_duration_ms})
     */
    public ScheduledAction execute(ScheduledAction job) {
        if (!running.add(job.uuid)) {
            Log.warnf("SCHEDULED-ACTIONS [%s] still running from a previous start, skip", job.name);
            job.last_status = STATUS_RUNNING;
            job.last_message = "skipped: previous run still in progress";
            return job;
        }
        String startedAt = OffsetDateTime.now(zone()).toString();
        long start = System.currentTimeMillis();
        try {
            markRunning(job.uuid, startedAt);
            if (job.body == null || job.body.isBlank()) {
                throw new IllegalStateException("body is empty");
            }
            Log.infof("SCHEDULED-ACTIONS [%s] start, body length=%d", job.name, job.body.length());
            scriptService.execute(job.body, job.asAction(), null, null, null, new java.util.HashMap<>());
            job.last_status = STATUS_OK;
            job.last_message = null;
        } catch (Throwable e) {
            Log.errorf(e, "SCHEDULED-ACTIONS [%s] failed", job.name);
            job.last_status = STATUS_ERROR;
            String msg = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
            job.last_message = msg.length() > MAX_MESSAGE ? msg.substring(0, MAX_MESSAGE) : msg;
        } finally {
            running.remove(job.uuid);
        }
        job.last_run = startedAt;
        job.last_duration_ms = System.currentTimeMillis() - start;
        Log.infof("SCHEDULED-ACTIONS [%s] %s in %d ms", job.name, job.last_status, job.last_duration_ms);
        recordOutcome(job);
        return job;
    }

    /** Moves next_run from {@code expected} to {@code next}; false when another instance moved it first. */
    boolean claim(String uuid, String expected, String next) {
        String sql = "UPDATE " + SCHEDULED_ACTIONS + " SET next_run = ? WHERE uuid = ? AND "
                     + (expected == null ? "next_run IS NULL" : "next_run = ?");
        try (Connection c = jdbcRepository.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, next);
            ps.setString(2, uuid);
            if (expected != null) {
                ps.setString(3, expected);
            }
            return ps.executeUpdate() == 1;
        } catch (Exception e) {
            Log.errorf(e, "SCHEDULED-ACTIONS claim failed for %s", uuid);
            return false;
        }
    }

    private void markRunning(String uuid, String startedAt) {
        update("UPDATE " + SCHEDULED_ACTIONS + " SET last_status = ?, last_run = ? WHERE uuid = ?",
                STATUS_RUNNING, startedAt, uuid);
    }

    private void recordOutcome(ScheduledAction job) {
        String sql = "UPDATE " + SCHEDULED_ACTIONS
                     + " SET last_status = ?, last_message = ?, last_run = ?, last_duration_ms = ? WHERE uuid = ?";
        try (Connection c = jdbcRepository.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, job.last_status);
            ps.setString(2, job.last_message);
            ps.setString(3, job.last_run);
            ps.setLong(4, job.last_duration_ms);
            ps.setString(5, job.uuid);
            ps.executeUpdate();
        } catch (Exception e) {
            Log.errorf(e, "SCHEDULED-ACTIONS cannot record outcome of %s", job.name);
        }
    }

    private void update(String sql, String... params) {
        try (Connection c = jdbcRepository.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            ps.executeUpdate();
        } catch (Exception e) {
            Log.errorf(e, "SCHEDULED-ACTIONS update failed: %s", sql);
        }
    }
}
