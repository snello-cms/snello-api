package io.snello.model.ai;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.model.MapReads;
import io.snello.model.MapWrites;

import java.util.Map;

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

    public AiServiceAccount() {
    }

    public AiServiceAccount(Map<String, Object> map) {
        fromMap(map);
    }

    public void fromMap(Map<String, Object> map) {
        uuid = MapReads.str(map, "uuid");
        project_uuid = MapReads.str(map, "project_uuid");
        name = MapReads.str(map, "name");
        host = MapReads.str(map, "host");
        realm = MapReads.str(map, "realm");
        client_id = MapReads.str(map, "client_id");
        client_secret = MapReads.str(map, "client_secret");
        enabled = MapReads.bool(map, "enabled", true);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = MapWrites.map();
        MapWrites.put(m, "uuid", uuid);
        MapWrites.put(m, "project_uuid", project_uuid);
        MapWrites.put(m, "name", name);
        MapWrites.put(m, "host", host);
        MapWrites.put(m, "realm", realm);
        MapWrites.put(m, "client_id", client_id);
        MapWrites.put(m, "client_secret", client_secret);
        MapWrites.putBool(m, "enabled", enabled);
        return m;
    }

    /** Public view without secret. */
    public Map<String, Object> toPublicMap() {
        Map<String, Object> m = toMap();
        m.remove("client_secret");
        return m;
    }
}
