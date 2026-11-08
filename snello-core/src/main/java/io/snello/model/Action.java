package io.snello.model;

import io.snello.util.MapReads;
import io.snello.util.MapWrites;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.Map;

@RegisterForReflection
public class Action {

    public String uuid;
    public String name;
    public String description;
    public String metadata_name; // TABLE
    public String condition; // PERSIST, MERGE, DELETE
    public String phase; // PRE, POST
    public String body;

    public Action() {
    }

    public Action(Map<String, Object> map) {
        fromMap(map, this);
    }

    public Action fromMap(Map<String, Object> map, Action template) {
        if (map == null || template == null) {
            return template;
        }
        template.uuid = MapReads.str(map, "uuid");
        template.name = MapReads.str(map, "name");
        template.description = MapReads.str(map, "description");
        template.metadata_name = MapReads.str(map, "metadata_name");
        template.condition = MapReads.str(map, "condition");
        template.phase = MapReads.str(map, "phase");
        template.body = MapReads.str(map, "body");
        return template;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = MapWrites.map();
        MapWrites.put(map, "uuid", uuid);
        MapWrites.put(map, "name", name);
        MapWrites.put(map, "description", description);
        MapWrites.put(map, "metadata_name", metadata_name);
        MapWrites.put(map, "condition", condition);
        MapWrites.put(map, "phase", phase);
        MapWrites.put(map, "body", body);
        return map;
    }

    @Override
    public String toString() {
        return "Action{" +
               "uuid='" + uuid + '\'' +
               ", name='" + name + '\'' +
               ", description='" + description + '\'' +
               ", metadata_name='" + metadata_name + '\'' +
               ", condition='" + condition + '\'' +
               ", phase='" + phase + '\'' +
               ", body='" + body + '\'' +
               '}';
    }
}
