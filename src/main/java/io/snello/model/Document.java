package io.snello.model;

import java.util.Map;

public class Document {

    public String uuid;
    public String name;
    public String original_name;
    public String path;
    public String mimetype;
    public int size;


    public String table_name;
    public String table_key;



    public Document() {
    }

    public Document(Map<String, Object> map) {
        super();
        fromMap(map, this);
    }

    public Document fromMap(Map<String, Object> map, Document document) {
        if (map.get("uuid") instanceof String) {
            document.uuid = (String) map.get("uuid");
        }
        if (map.get("name") instanceof String) {
            document.name = (String) map.get("name");
        }
        if (map.get("original_name") instanceof String) {
            document.original_name = (String) map.get("original_name");
        }
        if (map.get("size") instanceof String) {
            document.size = (Integer) map.get("size");
        }
        if (map.get("path") instanceof String) {
            document.path = (String) map.get("path");
        }
        if (map.get("mimetype") instanceof String) {
            document.mimetype = (String) map.get("mimetype");
        }
        if (map.get("mimetype") instanceof String) {
            document.mimetype = (String) map.get("mimetype");
        }
        if (map.get("table_name") instanceof String) {
            document.table_name = (String) map.get("table_name");
        }
        if (map.get("table_key") instanceof String) {
            document.table_key = (String) map.get("table_key");
        }

        return document;
    }
}
