package io.snello.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.util.MapReads;
import io.snello.util.MapWrites;

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
        if (map == null || aiTool == null) {
            return aiTool;
        }
        aiTool.uuid = MapReads.str(map, "uuid");
        aiTool.name = MapReads.str(map, "name");
        aiTool.method_name = MapReads.str(map, "method_name");
        aiTool.description = MapReads.str(map, "description");
        aiTool.sql_query = MapReads.str(map, "sql_query");
        aiTool.parameters_schema = MapReads.str(map, "parameters_schema");
        aiTool.active = MapReads.bool(map, "active", false);
        return aiTool;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = MapWrites.map();
        MapWrites.put(map, "uuid", uuid);
        MapWrites.put(map, "name", name);
        MapWrites.put(map, "method_name", method_name);
        MapWrites.put(map, "description", description);
        MapWrites.put(map, "sql_query", sql_query);
        MapWrites.put(map, "parameters_schema", parameters_schema);
        MapWrites.putBool(map, "active", active);
        return map;
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
}
