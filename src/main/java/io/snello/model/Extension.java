package io.snello.model;

import java.util.Map;

public class Extension {

    public String uuid;
    public String name;
    public String icon;
    public String description;
    public String tag_name;
    public String library_path;

    public Extension() {
    }

    public Extension(Map<String, Object> map) {
        super();
        fromMap(map, this);
    }

    public Extension fromMap(Map<String, Object> map, Extension extension) {
        if (map.get("uuid") instanceof String) {
            extension.uuid = (String) map.get("uuid");
        }
        if (map.get("name") instanceof String) {
            extension.name = (String) map.get("name");
        }
        if (map.get("icon") instanceof String) {
            extension.icon = (String) map.get("icon");
        }
        if (map.get("description") instanceof String) {
            extension.description = (String) map.get("description");
        }
        if (map.get("tag_name") instanceof String) {
            extension.tag_name = (String) map.get("tag_name");
        }
        if (map.get("library_path") instanceof String) {
            extension.library_path = (String) map.get("library_path");
        }
        return extension;
    }
}
