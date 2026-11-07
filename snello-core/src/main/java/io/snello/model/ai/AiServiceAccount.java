package io.snello.model.ai;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.*;

@RegisterForReflection
public class AiServiceAccount {
    public String uuid;
    public String project_uuid;
    public String name;
    public String host;
    public String realm;
    public String client_id;
    public String client_secret;
    public boolean enabled = true;

    public AiServiceAccount() {}
    public AiServiceAccount(Map<String, Object> map) { fromMap(map); }

    public void fromMap(Map<String, Object> map) {
        if (map.get("uuid") instanceof String s) uuid = s;
        if (map.get("project_uuid") instanceof String s) project_uuid = s;
        if (map.get("name") instanceof String s) name = s;
        if (map.get("host") instanceof String s) host = s;
        if (map.get("realm") instanceof String s) realm = s;
        if (map.get("client_id") instanceof String s) client_id = s;
        if (map.get("client_secret") instanceof String s) client_secret = s;
        if (map.get("enabled") instanceof Boolean b) enabled = b;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (uuid != null) m.put("uuid", uuid);
        if (project_uuid != null) m.put("project_uuid", project_uuid);
        if (name != null) m.put("name", name);
        if (host != null) m.put("host", host);
        if (realm != null) m.put("realm", realm);
        if (client_id != null) m.put("client_id", client_id);
        if (client_secret != null) m.put("client_secret", client_secret);
        m.put("enabled", enabled);
        return m;
    }

    /** Public view without secret. */
    public Map<String, Object> toPublicMap() {
        Map<String, Object> m = toMap();
        m.remove("client_secret");
        return m;
    }
}
