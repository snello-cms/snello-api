package io.snello.model.ai;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.model.MapReads;
import io.snello.model.MapWrites;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@RegisterForReflection
public class AiSkill {
    public String uuid;
    public String project_uuid;
    public String name;
    public String description;
    public String instructions;
    public String allowed_roles;
    public boolean enabled = true;

    public AiSkill() {
    }

    public AiSkill(Map<String, Object> map) {
        fromMap(map);
    }

    public void fromMap(Map<String, Object> map) {
        uuid = MapReads.str(map, "uuid");
        project_uuid = MapReads.str(map, "project_uuid");
        name = MapReads.str(map, "name");
        description = MapReads.str(map, "description");
        instructions = MapReads.str(map, "instructions");
        allowed_roles = MapReads.str(map, "allowed_roles");
        enabled = MapReads.bool(map, "enabled", true);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = MapWrites.map();
        MapWrites.put(m, "uuid", uuid);
        MapWrites.put(m, "project_uuid", project_uuid);
        MapWrites.put(m, "name", name);
        MapWrites.put(m, "description", description);
        MapWrites.put(m, "instructions", instructions);
        m.put("allowed_roles", allowed_roles == null ? "" : allowed_roles);
        MapWrites.putBool(m, "enabled", enabled);
        return m;
    }

    public Set<String> allowedRoleSet() {
        if (allowed_roles == null || allowed_roles.isBlank()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String part : allowed_roles.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    public boolean allowedFor(Set<String> callerRoles) {
        Set<String> allowed = allowedRoleSet();
        if (allowed.isEmpty()) {
            return true;
        }
        if (callerRoles == null || callerRoles.isEmpty()) {
            return false;
        }
        for (String r : allowed) {
            for (String c : callerRoles) {
                if (r.equalsIgnoreCase(c)) {
                    return true;
                }
            }
        }
        return false;
    }
}
