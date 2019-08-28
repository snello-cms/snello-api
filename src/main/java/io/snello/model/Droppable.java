package io.snello.model;

import java.util.Map;

public class Droppable {

    public String uuid;
    public String name;
    public String description;
    public String draggables;
    public String html;
    public String values;
    public String dynamics;

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
        if (map.get("values") instanceof String) {
            draggable.values = (String) map.get("values");
        }
        if (map.get("dynamics") instanceof String) {
            draggable.dynamics = (String) map.get("dynamics");
        }
        return draggable;
    }
}
