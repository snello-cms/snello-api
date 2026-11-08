package io.snello.model.ai;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.model.MapReads;
import io.snello.model.MapWrites;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@RegisterForReflection
public class AiMcpTool {
    public String uuid;
    public String project_uuid;
    public String name;
    public String description;
    public String http_method = "GET";
    public String url;
    public String parameters_json = "{\"type\":\"object\",\"properties\":{}}";
    public String arg_binding = "QUERY";
    public String arg_names_json;
    public String fixed_query_json;
    public String catalog_page_path_template;
    public String result_type;
    public String result_path;
    public boolean authenticated;
    public String service_account_uuid;
    public String allowed_roles;
    public boolean enabled = true;

    public AiMcpTool() {
    }

    public AiMcpTool(Map<String, Object> map) {
        fromMap(map);
    }

    public void fromMap(Map<String, Object> map) {
        uuid = MapReads.str(map, "uuid");
        project_uuid = MapReads.str(map, "project_uuid");
        name = MapReads.str(map, "name");
        description = MapReads.str(map, "description");
        String method = MapReads.str(map, "http_method");
        if (method != null) {
            http_method = method;
        }
        url = MapReads.str(map, "url");
        String params = MapReads.str(map, "parameters_json");
        if (params != null) {
            parameters_json = params;
        }
        String binding = MapReads.str(map, "arg_binding");
        if (binding != null) {
            arg_binding = binding;
        }
        arg_names_json = MapReads.str(map, "arg_names_json");
        fixed_query_json = MapReads.str(map, "fixed_query_json");
        catalog_page_path_template = MapReads.str(map, "catalog_page_path_template");
        result_type = MapReads.str(map, "result_type");
        result_path = MapReads.str(map, "result_path");
        authenticated = MapReads.bool(map, "authenticated", false);
        service_account_uuid = MapReads.str(map, "service_account_uuid");
        allowed_roles = MapReads.str(map, "allowed_roles");
        enabled = MapReads.bool(map, "enabled", true);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = MapWrites.map();
        MapWrites.put(m, "uuid", uuid);
        MapWrites.put(m, "project_uuid", project_uuid);
        MapWrites.put(m, "name", name);
        MapWrites.put(m, "description", description);
        m.put("http_method", http_method == null ? "GET" : http_method);
        MapWrites.put(m, "url", url);
        m.put("parameters_json", parameters_json);
        m.put("arg_binding", arg_binding == null ? "QUERY" : arg_binding);
        MapWrites.put(m, "arg_names_json", arg_names_json);
        MapWrites.put(m, "fixed_query_json", fixed_query_json);
        MapWrites.put(m, "catalog_page_path_template", catalog_page_path_template);
        MapWrites.put(m, "result_type", result_type);
        MapWrites.put(m, "result_path", result_path);
        MapWrites.putBool(m, "authenticated", authenticated);
        MapWrites.put(m, "service_account_uuid", service_account_uuid);
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
