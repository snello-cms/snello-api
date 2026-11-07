package io.snello.model.ai;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.*;

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

    public HistoryEntry() {}
    public HistoryEntry(Map<String, Object> map) { fromMap(map); }

    public void fromMap(Map<String, Object> map) {
        if (map.get("uuid") instanceof String s) uuid = s;
        if (map.get("occurred_at") != null) occurred_at = String.valueOf(map.get("occurred_at"));
        if (map.get("channel") instanceof String s) channel = s;
        if (map.get("user_id") instanceof String s) user_id = s;
        if (map.get("username") instanceof String s) username = s;
        if (map.get("type") instanceof String s) type = s;
        if (map.get("body") instanceof String s) body = s;
        if (map.get("session_id") instanceof String s) session_id = s;
        if (map.get("model") instanceof String s) model = s;
        if (map.get("recorded_at") != null) recorded_at = String.valueOf(map.get("recorded_at"));
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (uuid != null) m.put("uuid", uuid);
        if (occurred_at != null) m.put("occurred_at", occurred_at);
        if (channel != null) m.put("channel", channel);
        if (user_id != null) m.put("user_id", user_id);
        if (username != null) m.put("username", username);
        if (type != null) m.put("type", type);
        if (body != null) m.put("body", body);
        if (session_id != null) m.put("session_id", session_id);
        if (model != null) m.put("model", model);
        if (recorded_at != null) m.put("recorded_at", recorded_at);
        return m;
    }
}
