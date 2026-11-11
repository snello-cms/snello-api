package io.snello.service;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.snello.management.SnelloPlane;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The single timer behind {@code scheduledactions}: every tick it runs the rows that are due.
 * <pre>
 * snello.scheduled-actions.enabled=true          # false: the timer does nothing
 * snello.scheduled-actions.cron=0 *&#47;5 * * * ?    # tick, aligned to the clock (default every 5 minutes)
 * snello.scheduled-actions.timezone=Europe/Rome  # zone of the jobs' cron expressions (default: server zone)
 * </pre>
 * A job is run at the first tick on or after its cron time, so the tick is the precision: with a 5 minute tick a
 * job "every minute" runs every 5 minutes. Control plane only (query nodes must not run jobs).
 */
@ApplicationScoped
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class ScheduledActionTimer {

    @Inject
    ScheduledActionService service;

    @ConfigProperty(name = "snello.scheduled-actions.enabled", defaultValue = "true")
    boolean enabled;

    @Scheduled(identity = "snello-scheduled-actions", cron = "{snello.scheduled-actions.cron:0 */5 * * * ?}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        if (!enabled) {
            Log.debug("SCHEDULED-ACTIONS timer disabled (snello.scheduled-actions.enabled=false)");
            return;
        }
        try {
            service.runDue();
        } catch (Exception e) {
            // never let an error stop the schedule: the next tick retries
            Log.errorf(e, "SCHEDULED-ACTIONS tick failed");
        }
    }
}
