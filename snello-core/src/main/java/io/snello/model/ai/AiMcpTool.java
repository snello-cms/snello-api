package io.snello.model.ai;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.*;

@RegisterForReflection
public class AiMcpTool {
    public String uuid;
    public String project_uuid;
    public String name;
    public String description;
    public String http_method = "GET";
    public String url;
    public String parameters_json = "{\"type\":\"object\",\"properties\":{}}";
    public String arg_binding = "QUERY"; // QUERY | BODY
    public String arg_names_json;
    public String fixed_query_json;
    public String catalog_page_path_template;
    public String result_type;
    public String result_path;
    public boolean authenticated;
    public String service_account_uuid;
    public String allowed_roles;
    public boolean enabled = true;

    public AiMcpTool() {}
    public AiMcpTool(Map<String, Object> map) { fromMap(map); }

    public void fromMap(Map<String, Object> map) {
        if (map.get("uuid") instanceof String s) uuid = s;
        if (map.get("project_uuid") instanceof String s) project_uuid = s;
        if (map.get("name") instanceof String s) name = s;
        if (map.get("description") instanceof String s) description = s;
        if (map.get("http_method") instanceof String s) http_method = s;
        if (map.get("url") instanceof String s) url = s;
        if (map.get("parameters_json") instanceof String s) parameters_json = s;
        if (map.get("arg_binding") instanceof String s) arg_binding = s;
        if (map.get("arg_names_json") instanceof String s) arg_names_json = s;
        if (map.get("fixed_query_json") instanceof String s) fixed_query_json = s;
        if (map.get("catalog_page_path_template") instanceof String s) catalog_page_path_template = s;
        if (map.get("result_type") instanceof String s) result_type = s;
        if (map.get("result_path") instanceof String s) result_path = s;
        if (map.get("authenticated") instanceof Boolean b) authenticated = b;
        if (map.get("service_account_uuid") instanceof String s) service_account_uuid = s;
        if (map.get("allowed_roles") instanceof String s) allowed_roles = s;
        if (map.get("enabled") instanceof Boolean b) enabled = b;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (uuid != null) m.put("uuid", uuid);
        if (project_uuid != null) m.put("project_uuid", project_uuid);
        if (name != null) m.put("name", name);
        if (description != null) m.put("description", description);
        m.put("http_method", http_method == null ? "GET" : http_method);
        if (url != null) m.put("url", url);
        m.put("parameters_json", parameters_json);
        m.put("arg_binding", arg_binding == null ? "QUERY" : arg_binding);
        if (arg_names_json != null) m.put("arg_names_json", arg_names_json);
        if (fixed_query_json != null) m.put("fixed_query_json", fixed_query_json);
        if (catalog_page_path_template != null) m.put("catalog_page_path_template", catalog_page_path_template);
        if (result_type != null) m.put("result_type", result_type);
        if (result_path != null) m.put("result_path", result_path);
        m.put("authenticated", authenticated);
        if (service_account_uuid != null) m.put("service_account_uuid", service_account_uuid);
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
