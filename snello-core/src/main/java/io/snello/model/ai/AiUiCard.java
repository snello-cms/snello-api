package io.snello.model.ai;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.util.MapReads;
import io.snello.util.MapWrites;

import java.util.Map;

@RegisterForReflection
public class AiUiCard {
    public String uuid;
    public String project_uuid;
    public String name;
    public String result_type;
    public String template;
    public boolean enabled = true;

    public AiUiCard() {
    }

    public AiUiCard(Map<String, Object> map) {
        fromMap(map);
    }

    public void fromMap(Map<String, Object> map) {
        uuid = MapReads.str(map, "uuid");
        project_uuid = MapReads.str(map, "project_uuid");
        name = MapReads.str(map, "name");
        result_type = MapReads.str(map, "result_type");
        template = MapReads.str(map, "template");
        enabled = MapReads.bool(map, "enabled", true);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = MapWrites.map();
        MapWrites.put(m, "uuid", uuid);
        MapWrites.put(m, "project_uuid", project_uuid);
        MapWrites.put(m, "name", name);
        MapWrites.put(m, "result_type", result_type);
        MapWrites.put(m, "template", template);
        MapWrites.putBool(m, "enabled", enabled);
        return m;
    }
}
