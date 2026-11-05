package io.snello.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.HashMap;
import java.util.Map;

@RegisterForReflection
public class AiTool {

    public String uuid;
    public String name;
    public String method_name;
    public String description;
    public String sql_query;
    public String parameters_schema;
    public boolean active;

    public AiTool() {
    }

    public AiTool(Map<String, Object> map) {
        fromMap(map, this);
    }

    public AiTool fromMap(Map<String, Object> map, AiTool aiTool) {
        if (map.get("uuid") instanceof String) {
            aiTool.uuid = (String) map.get("uuid");
        }
        if (map.get("name") instanceof String) {
            aiTool.name = (String) map.get("name");
        }
        if (map.get("method_name") instanceof String) {
            aiTool.method_name = (String) map.get("method_name");
        }
        if (map.get("description") instanceof String) {
            aiTool.description = (String) map.get("description");
        }
        if (map.get("sql_query") instanceof String) {
            aiTool.sql_query = (String) map.get("sql_query");
        }
        if (map.get("parameters_schema") instanceof String) {
            aiTool.parameters_schema = (String) map.get("parameters_schema");
        }
        if (map.get("active") instanceof Boolean) {
            aiTool.active = (Boolean) map.get("active");
        }
        return aiTool;
    }

    @Override
    public String toString() {
        return "AiTool{" +
                "uuid='" + uuid + '\'' +
                ", name='" + name + '\'' +
                ", method_name='" + method_name + '\'' +
                ", active=" + active +
                '}';
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (this.uuid != null) {
            map.put("uuid", this.uuid);
        }
        if (this.name != null) {
            map.put("name", this.name);
        }
        if (this.method_name != null) {
            map.put("method_name", this.method_name);
        }
        if (this.description != null) {
            map.put("description", this.description);
        }
        if (this.sql_query != null) {
            map.put("sql_query", this.sql_query);
        }
        if (this.parameters_schema != null) {
            map.put("parameters_schema", this.parameters_schema);
        }
        map.put("active", this.active);
        return map;
    }
}
