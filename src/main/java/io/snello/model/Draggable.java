package io.snello.model;

import java.util.Map;

public class Draggable {

    public String uuid;
    public String name;
    public String description;
    public String template;
    public String style;
    public String image; //(serve per la composizione a drag and drop nei droppables)
    public String vars; // (separate da ;)
    public String dynamics; // (separate da ;)

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
        if (map.get("vars") instanceof String) {
            draggable.vars = (String) map.get("vars");
        }
        if (map.get("dynamics") instanceof String) {
            draggable.dynamics = (String) map.get("dynamics");
        }
        return draggable;
    }
}
