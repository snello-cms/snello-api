package io.snello.model.ai;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.util.MapReads;
import io.snello.util.MapWrites;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@RegisterForReflection
public class AiProject {
    public String uuid;
    public String name;
    public String description;
    public String global_message;
    public String sso_group;
    public String roles;

    public AiProject() {
    }

    public AiProject(Map<String, Object> map) {
        fromMap(map);
    }

    public void fromMap(Map<String, Object> map) {
        uuid = MapReads.str(map, "uuid");
        name = MapReads.str(map, "name");
        description = MapReads.str(map, "description");
        global_message = MapReads.str(map, "global_message");
        sso_group = MapReads.str(map, "sso_group");
        roles = MapReads.str(map, "roles");
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = MapWrites.map();
        MapWrites.put(m, "uuid", uuid);
        MapWrites.put(m, "name", name);
        m.put("description", description == null ? "" : description);
        m.put("global_message", global_message == null ? "" : global_message);
        m.put("sso_group", sso_group == null ? "" : sso_group);
        m.put("roles", roles == null ? "" : roles);
        return m;
    }

    public boolean isAccessibleTo(Set<String> tokenClaims) {
        if (sso_group == null || sso_group.isBlank()) {
            return true;
        }
        if (tokenClaims == null || tokenClaims.isEmpty()) {
            return false;
        }
        String wanted = sso_group.trim();
        for (String claim : tokenClaims) {
            if (wanted.equals(claim) || wanted.equalsIgnoreCase(claim)) {
                return true;
            }
        }
        return false;
    }

    public Set<String> roleSet() {
        if (roles == null || roles.isBlank()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String part : roles.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}
