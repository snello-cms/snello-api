package io.snello.model.ai;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.*;

@RegisterForReflection
public class AiUiCard {
    public String uuid;
    public String project_uuid;
    public String name;
    public String result_type;
    public String template;
    public boolean enabled = true;

    public AiUiCard() {}
    public AiUiCard(Map<String, Object> map) { fromMap(map); }

    public void fromMap(Map<String, Object> map) {
        if (map.get("uuid") instanceof String s) uuid = s;
        if (map.get("project_uuid") instanceof String s) project_uuid = s;
        if (map.get("name") instanceof String s) name = s;
        if (map.get("result_type") instanceof String s) result_type = s;
        if (map.get("template") instanceof String s) template = s;
        if (map.get("enabled") instanceof Boolean b) enabled = b;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (uuid != null) m.put("uuid", uuid);
        if (project_uuid != null) m.put("project_uuid", project_uuid);
        if (name != null) m.put("name", name);
        if (result_type != null) m.put("result_type", result_type);
        if (template != null) m.put("template", template);
        m.put("enabled", enabled);
        return m;
    }
}
