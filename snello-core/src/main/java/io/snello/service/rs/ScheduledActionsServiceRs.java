package io.snello.service.rs;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.api.service.AbstractServiceRs;
import io.snello.management.SnelloPlane;
import io.snello.model.ScheduledAction;
import io.snello.service.ApiService;
import io.snello.service.ScheduledActionService;
import io.snello.util.MapReads;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.snello.management.AppConstants.SCHEDULED_ACTIONS;
import static io.snello.management.AppConstants.SCHEDULED_ACTIONS_PATH;

/**
 * CRUD for {@code scheduledactions}, plus:
 * <ul>
 *     <li>{@code GET  /api/scheduledactions/preview?frequency=0 17 * * *&count=5} next fire times of a cron</li>
 *     <li>{@code POST /api/scheduledactions/{id}/run} run a job now (Admin/Manager, like every config write)</li>
 * </ul>
 * {@code next_run} is computed here on save; {@code last_*} are owned by the scheduler and ignored on input.
 */
@Path(SCHEDULED_ACTIONS_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class ScheduledActionsServiceRs extends AbstractServiceRs {

    private static final List<String> SCHEDULER_OWNED = List.of("last_run", "last_status", "last_message",
            "last_duration_ms", "next_run");

    @Inject
    ScheduledActionService scheduledActionService;

    @Inject
    ScheduledActionsServiceRs(ApiService apiService) {
        super(apiService, SCHEDULED_ACTIONS, "name asc");
    }

    public ScheduledActionsServiceRs() {
    }

    @Override
    protected void prePersist(Map<String, Object> object) throws Exception {
        SCHEDULER_OWNED.forEach(object::remove);
        if (MapReads.str(object, "name") == null || MapReads.str(object, "name").isBlank()) {
            throw new BadRequestException("name is required");
        }
        if (object.get("enabled") == null) {
            object.put("enabled", true);
        }
        applyNextRun(object, MapReads.str(object, "frequency"), MapReads.bool(object, "enabled", true));
    }

    @Override
    protected void preUpdate(String id, Map<String, Object> object) throws Exception {
        SCHEDULER_OWNED.forEach(object::remove);
        ScheduledAction stored = scheduledActionService.find(id);
        if (stored == null) {
            throw new NotFoundException("scheduled action " + id + " not found");
        }
        String frequency = object.containsKey("frequency") ? MapReads.str(object, "frequency") : stored.frequency;
        boolean enabled = object.containsKey("enabled") ? MapReads.bool(object, "enabled", true) : stored.enabled;
        applyNextRun(object, frequency, enabled);
    }

    /** Validates the cron and sets next_run from now, so editing or re-enabling never fires past runs. */
    private void applyNextRun(Map<String, Object> object, String frequency, boolean enabled) {
        String next;
        try {
            next = scheduledActionService.nextRunFromNow(frequency);
        } catch (IllegalArgumentException e) {
            Log.infof("scheduledaction %s rejected: %s", object.get("name"), e.getMessage());
            throw new BadRequestException(e.getMessage());
        }
        object.put("next_run", enabled ? next : null);
        Log.infof("scheduledaction %s: frequency='%s' enabled=%s next_run=%s",
                object.get("name"), frequency, enabled, object.get("next_run"));
    }

    @GET
    @Path("/preview")
    public Response preview(@QueryParam("frequency") String frequency, @QueryParam("count") Integer count) {
        int n = count == null ? 5 : Math.max(1, Math.min(count, 20));
        Log.infof("scheduledactions preview frequency='%s' count=%d zone=%s", frequency, n, scheduledActionService.zone());
        List<String> runs = new ArrayList<>();
        try {
            ZonedDateTime cursor = ZonedDateTime.now(scheduledActionService.zone());
            for (int i = 0; i < n; i++) {
                OffsetDateTime next = scheduledActionService.nextRun(frequency, cursor);
                if (next == null) {
                    break;
                }
                runs.add(next.toString());
                cursor = next.atZoneSameInstant(scheduledActionService.zone());
            }
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("valid", false, "error", e.getMessage())).build();
        }
        return Response.ok(Map.of("valid", true, "zone", scheduledActionService.zone().getId(), "next_runs", runs))
                .build();
    }

    @POST
    @Path("/{id}/run")
    @Consumes(MediaType.WILDCARD) // no body: callers need not send a JSON content type
    public Response runNow(@PathParam("id") String id) throws Exception {
        Log.infof("scheduledaction %s: run now requested", id);
        ScheduledAction job = scheduledActionService.find(id);
        if (job == null) {
            throw new NotFoundException("scheduled action " + id + " not found");
        }
        ScheduledAction outcome = scheduledActionService.execute(job);
        return Response.ok(outcome.toMap()).build();
    }
}
