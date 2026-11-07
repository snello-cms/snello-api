package io.snello.model.ai;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.*;

@RegisterForReflection
public class AiSkill {
    public String uuid;
    public String project_uuid;
    public String name;
    public String description;
    public String instructions;
    public String allowed_roles; // comma-separated
    public boolean enabled = true;

    public AiSkill() {}
    public AiSkill(Map<String, Object> map) { fromMap(map); }

    public void fromMap(Map<String, Object> map) {
        if (map.get("uuid") instanceof String s) uuid = s;
        if (map.get("project_uuid") instanceof String s) project_uuid = s;
        if (map.get("name") instanceof String s) name = s;
        if (map.get("description") instanceof String s) description = s;
        if (map.get("instructions") instanceof String s) instructions = s;
        if (map.get("allowed_roles") instanceof String s) allowed_roles = s;
        if (map.get("enabled") instanceof Boolean b) enabled = b;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (uuid != null) m.put("uuid", uuid);
        if (project_uuid != null) m.put("project_uuid", project_uuid);
        if (name != null) m.put("name", name);
        if (description != null) m.put("description", description);
        if (instructions != null) m.put("instructions", instructions);
        m.put("allowed_roles", allowed_roles == null ? "" : allowed_roles);
        m.put("enabled", enabled);
        return m;
    }

    public Set<String> allowedRoleSet() {
        if (allowed_roles == null || allowed_roles.isBlank()) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        for (String part : allowed_roles.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    public boolean allowedFor(Set<String> callerRoles) {
        Set<String> allowed = allowedRoleSet();
        if (allowed.isEmpty()) return true;
        if (callerRoles == null || callerRoles.isEmpty()) return false;
        for (String r : allowed) {
            for (String c : callerRoles) {
                if (r.equalsIgnoreCase(c)) return true;
            }
        }
        return false;
    }
}
