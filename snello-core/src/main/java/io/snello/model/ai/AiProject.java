package io.snello.model.ai;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.*;

@RegisterForReflection
public class AiProject {
    public String uuid;
    public String name;
    public String description;
    public String global_message;
    public String sso_group;
    public String roles;

    public AiProject() {}
    public AiProject(Map<String, Object> map) { fromMap(map); }

    public void fromMap(Map<String, Object> map) {
        if (map.get("uuid") instanceof String s) uuid = s;
        if (map.get("name") instanceof String s) name = s;
        if (map.get("description") instanceof String s) description = s;
        if (map.get("global_message") instanceof String s) global_message = s;
        if (map.get("sso_group") instanceof String s) sso_group = s;
        if (map.get("roles") instanceof String s) roles = s;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (uuid != null) m.put("uuid", uuid);
        if (name != null) m.put("name", name);
        m.put("description", description == null ? "" : description);
        m.put("global_message", global_message == null ? "" : global_message);
        m.put("sso_group", sso_group == null ? "" : sso_group);
        m.put("roles", roles == null ? "" : roles);
        return m;
    }

    public boolean isAccessibleTo(Set<String> tokenClaims) {
        if (sso_group == null || sso_group.isBlank()) return true;
        if (tokenClaims == null || tokenClaims.isEmpty()) return false;
        String wanted = sso_group.trim();
        for (String claim : tokenClaims) {
            if (wanted.equals(claim) || wanted.equalsIgnoreCase(claim)) return true;
        }
        return false;
    }

    public Set<String> roleSet() {
        if (roles == null || roles.isBlank()) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        for (String part : roles.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
