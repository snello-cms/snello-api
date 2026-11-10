package io.snello.service;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.snello.model.Action;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Optional;

/**
 * Simple scheduler: runs one JavaScript snippet on a cron, with the same bindings an Action gets
 * ({@code db}, {@code mail}, {@code documents}, {@code metadata}).
 * <p>
 * Disabled by default ({@code snello.scheduler.cron=off}). Example: remind renters whose rental ends tomorrow.
 * <pre>
 * snello.scheduler.cron=0 0 8 * * ?
 * snello.scheduler.script-file=/data/scheduler.js
 * </pre>
 * {@code snello.scheduler.script} (inline JS) is used when no file is set. Failures are logged and never stop the
 * schedule; overlapping runs are skipped.
 */
@ApplicationScoped
public class ScheduledScriptJob {

    public static final String JOB_NAME = "scheduled-script";

    @Inject
    ScriptService scriptService;

    @ConfigProperty(name = "snello.scheduler.script")
    Optional<String> inlineScript;

    @ConfigProperty(name = "snello.scheduler.script-file")
    Optional<String> scriptFile;

    @Scheduled(identity = "snello-scheduled-script", cron = "{snello.scheduler.cron:off}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        runOnce();
    }

    /** Package-visible so tests (and an admin endpoint, later) can trigger a run without waiting for the cron. */
    public boolean runOnce() {
        String js;
        try {
            js = loadScript();
        } catch (Exception e) {
            Log.errorf(e, "SCHEDULER cannot read script file %s", scriptFile.orElse(null));
            return false;
        }
        if (js == null || js.isBlank()) {
            Log.warn("SCHEDULER fired but no snello.scheduler.script / script-file is configured: nothing to do");
            return false;
        }
        long start = System.currentTimeMillis();
        Log.infof("SCHEDULER run start, script length=%d", js.length());
        try {
            Action action = new Action();
            action.name = JOB_NAME;
            action.phase = "SCHEDULED";
            scriptService.execute(js, action, null, null, null, new HashMap<>());
            Log.infof("SCHEDULER run ok in %d ms", System.currentTimeMillis() - start);
            return true;
        } catch (Exception e) {
            Log.errorf(e, "SCHEDULER run failed after %d ms", System.currentTimeMillis() - start);
            return false;
        }
    }

    private String loadScript() throws Exception {
        if (scriptFile.isPresent() && !scriptFile.get().isBlank()) {
            Log.infof("SCHEDULER loading script from file %s", scriptFile.get());
            return Files.readString(Path.of(scriptFile.get()));
        }
        return inlineScript.orElse(null);
    }
}
