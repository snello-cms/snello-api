package io.snello.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.HashMap;
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
        super();
        fromMap(map, this);
    }

    public Action fromMap(Map<String, Object> map, Action template) {
        if (map.get("uuid") instanceof String) {
            template.uuid = (String) map.get("uuid");
        }
        if (map.get("name") instanceof String) {
            template.name = (String) map.get("name");
        }
        if (map.get("description") instanceof String) {
            template.description = (String) map.get("description");
        }

        if (map.get("metadata_name") instanceof String) {
            template.metadata_name = (String) map.get("metadata_name");
        }

        if (map.get("condition") instanceof String) {
            template.condition = (String) map.get("condition");
        }
        if (map.get("phase") instanceof String) {
            template.phase = (String) map.get("phase");
        }
        if (map.get("body") instanceof String) {
            template.body = (String) map.get("body");
        }
        return template;
    }


    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (this.uuid != null) {
            map.put("uuid", this.uuid);
        }
        if (this.name != null) {
            map.put("name", this.name);
        }
        if (this.description != null) {
            map.put("description", this.description);
        }
        if (this.metadata_name != null) {
            map.put("metadata_name", this.metadata_name);
        }
        if (this.condition != null) {
            map.put("condition", this.condition);
        }
        if (this.phase != null) {
            map.put("phase", this.phase);
        }
        if (this.body != null) {
            map.put("body", this.body);
        }

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
