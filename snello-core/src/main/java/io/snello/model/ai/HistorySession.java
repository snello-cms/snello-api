package io.snello.model.ai;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.*;

@RegisterForReflection
public class HistorySession {
    public String uuid;
    public String session_id;
    public String user_id;
    public long message_count;
    public String started_at;

    public HistorySession() {}
    public HistorySession(Map<String, Object> map) { fromMap(map); }

    public void fromMap(Map<String, Object> map) {
        if (map.get("uuid") instanceof String s) uuid = s;
        if (map.get("session_id") instanceof String s) session_id = s;
        if (map.get("user_id") instanceof String s) user_id = s;
        Object mc = map.get("message_count");
        if (mc instanceof Number n) message_count = n.longValue();
        if (map.get("started_at") != null) started_at = String.valueOf(map.get("started_at"));
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (uuid != null) m.put("uuid", uuid);
        if (session_id != null) m.put("session_id", session_id);
        if (user_id != null) m.put("user_id", user_id);
        m.put("message_count", message_count);
        if (started_at != null) m.put("started_at", started_at);
        return m;
    }
}
