package io.snello.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.util.MapReads;
import io.snello.util.MapWrites;

import java.util.Map;

/**
 * A JavaScript job run on a schedule by {@link io.snello.service.ScheduledActionTimer}.
 * <p>
 * {@code frequency} is a cron expression: 5 fields (unix, {@code 0 17 * * *}) or 6-7 fields (Quartz, with seconds,
 * {@code 0 0 17 * * ?}). {@code body} gets the same bindings as an {@link Action} ({@code db}, {@code mail},
 * {@code http}, {@code documents}, {@code metadata}, {@code log}).
 * <p>
 * {@code next_run}, {@code last_*} are written by the scheduler (ISO-8601 offset date-times), not by the admin.
 */
@RegisterForReflection
public class ScheduledAction {

    public String uuid;
    public String name;
    public String description;
    public String frequency;
    public boolean enabled = true;
    public String body;
    public String next_run;
    public String last_run;
    public String last_status;
    public String last_message;
    public Long last_duration_ms;

    public ScheduledAction() {
    }

    public ScheduledAction(Map<String, Object> map) {
        fromMap(map, this);
    }

    public ScheduledAction fromMap(Map<String, Object> map, ScheduledAction target) {
        if (map == null || target == null) {
            return target;
        }
        target.uuid = MapReads.str(map, "uuid");
        target.name = MapReads.str(map, "name");
        target.description = MapReads.str(map, "description");
        target.frequency = MapReads.str(map, "frequency");
        target.enabled = MapReads.bool(map, "enabled", true);
        target.body = MapReads.str(map, "body");
        target.next_run = MapReads.str(map, "next_run");
        target.last_run = MapReads.str(map, "last_run");
        target.last_status = MapReads.str(map, "last_status");
        target.last_message = MapReads.str(map, "last_message");
        target.last_duration_ms = map.get("last_duration_ms") == null ? null
                : MapReads.longVal(map, "last_duration_ms", 0L);
        return target;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = MapWrites.map();
        MapWrites.put(map, "uuid", uuid);
        MapWrites.put(map, "name", name);
        MapWrites.put(map, "description", description);
        MapWrites.put(map, "frequency", frequency);
        MapWrites.putBool(map, "enabled", enabled);
        MapWrites.put(map, "body", body);
        MapWrites.put(map, "next_run", next_run);
        MapWrites.put(map, "last_run", last_run);
        MapWrites.put(map, "last_status", last_status);
        MapWrites.put(map, "last_message", last_message);
        MapWrites.put(map, "last_duration_ms", last_duration_ms);
        return map;
    }

    /** The {@link Action} handed to the script engine, so scripts see {@code action.name} as for table actions. */
    public Action asAction() {
        Action action = new Action();
        action.uuid = uuid;
        action.name = name;
        action.description = description;
        action.phase = "SCHEDULED";
        action.body = body;
        return action;
    }

    @Override
    public String toString() {
        return "ScheduledAction{" +
               "uuid='" + uuid + '\'' +
               ", name='" + name + '\'' +
               ", frequency='" + frequency + '\'' +
               ", enabled=" + enabled +
               ", next_run='" + next_run + '\'' +
               ", last_run='" + last_run + '\'' +
               ", last_status='" + last_status + '\'' +
               '}';
    }
}
