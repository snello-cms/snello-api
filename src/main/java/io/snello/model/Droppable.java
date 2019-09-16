package io.snello.model;

import java.util.Map;

public class Droppable {

    public String uuid;
    public String name;
    public String description;
    public String draggables;
    public String html;
    public String static_values;
    public String dynamic_values;

    public Droppable() {
    }

    public Droppable(Map<String, Object> map) {
        super();
        fromMap(map, this);
    }

    public Droppable fromMap(Map<String, Object> map, Droppable draggable) {
        if (map.get("uuid") instanceof String) {
            draggable.uuid = (String) map.get("uuid");
        }
        if (map.get("name") instanceof String) {
            draggable.name = (String) map.get("name");
        }
        if (map.get("description") instanceof String) {
            draggable.description = (String) map.get("description");
        }
        if (map.get("html") instanceof String) {
            draggable.html = (String) map.get("html");
        }
        if (map.get("draggables") instanceof String) {
            draggable.draggables = (String) map.get("draggables");
        }
        if (map.get("static_values") instanceof String) {
            draggable.static_values = (String) map.get("static_values");
        }
        if (map.get("dynamic_values") instanceof String) {
            draggable.dynamic_values = (String) map.get("dynamic_values");
        }
        return draggable;
    }
}
