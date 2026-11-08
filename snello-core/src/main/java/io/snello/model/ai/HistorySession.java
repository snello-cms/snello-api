package io.snello.model.ai;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.util.MapReads;
import io.snello.util.MapWrites;

import java.util.Map;

@RegisterForReflection
public class HistorySession {
    public String uuid;
    public String session_id;
    public String user_id;
    public long message_count;
    public String started_at;

    public HistorySession() {
    }

    public HistorySession(Map<String, Object> map) {
        fromMap(map);
    }

    public void fromMap(Map<String, Object> map) {
        uuid = MapReads.str(map, "uuid");
        session_id = MapReads.str(map, "session_id");
        user_id = MapReads.str(map, "user_id");
        message_count = MapReads.longVal(map, "message_count", 0L);
        started_at = MapReads.asString(map, "started_at");
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = MapWrites.map();
        MapWrites.put(m, "uuid", uuid);
        MapWrites.put(m, "session_id", session_id);
        MapWrites.put(m, "user_id", user_id);
        m.put("message_count", message_count);
        MapWrites.put(m, "started_at", started_at);
        return m;
    }
}
