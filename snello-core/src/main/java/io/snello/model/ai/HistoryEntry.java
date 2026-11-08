package io.snello.model.ai;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.model.MapReads;
import io.snello.model.MapWrites;

import java.util.Map;

@RegisterForReflection
public class HistoryEntry {
    public String uuid;
    public String occurred_at;
    public String channel;
    public String user_id;
    public String username;
    public String type;
    public String body;
    public String session_id;
    public String model;
    public String recorded_at;

    public HistoryEntry() {
    }

    public HistoryEntry(Map<String, Object> map) {
        fromMap(map);
    }

    public void fromMap(Map<String, Object> map) {
        uuid = MapReads.str(map, "uuid");
        occurred_at = MapReads.asString(map, "occurred_at");
        channel = MapReads.str(map, "channel");
        user_id = MapReads.str(map, "user_id");
        username = MapReads.str(map, "username");
        type = MapReads.str(map, "type");
        body = MapReads.str(map, "body");
        session_id = MapReads.str(map, "session_id");
        model = MapReads.str(map, "model");
        recorded_at = MapReads.asString(map, "recorded_at");
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = MapWrites.map();
        MapWrites.put(m, "uuid", uuid);
        MapWrites.put(m, "occurred_at", occurred_at);
        MapWrites.put(m, "channel", channel);
        MapWrites.put(m, "user_id", user_id);
        MapWrites.put(m, "username", username);
        MapWrites.put(m, "type", type);
        MapWrites.put(m, "body", body);
        MapWrites.put(m, "session_id", session_id);
        MapWrites.put(m, "model", model);
        MapWrites.put(m, "recorded_at", recorded_at);
        return m;
    }
}
