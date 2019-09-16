package io.snello.model;

import java.util.Map;

public class Draggable {

    public String uuid;
    public String name;
    public String description;
    public String template;
    public String style;
    public String image; //(serve per la composizione a drag and drop nei droppables)
    public String static_vars; // (separate da ;)
    public String dynamic_vars; // (separate da ;)

    public Draggable() {
    }

    public Draggable(Map<String, Object> map) {
        super();
        fromMap(map, this);
    }

    public Draggable fromMap(Map<String, Object> map, Draggable draggable) {
        if (map.get("uuid") instanceof String) {
            draggable.uuid = (String) map.get("uuid");
        }
        if (map.get("name") instanceof String) {
            draggable.name = (String) map.get("name");
        }
        if (map.get("description") instanceof String) {
            draggable.description = (String) map.get("description");
        }
        if (map.get("template") instanceof String) {
            draggable.template = (String) map.get("template");
        }
        if (map.get("style") instanceof String) {
            draggable.style = (String) map.get("style");
        }
        if (map.get("image") instanceof String) {
            draggable.image = (String) map.get("image");
        }
        if (map.get("static_vars") instanceof String) {
            draggable.static_vars = (String) map.get("static_vars");
        }
        if (map.get("dynamic_vars") instanceof String) {
            draggable.dynamic_vars = (String) map.get("dynamic_vars");
        }
        return draggable;
    }
}
