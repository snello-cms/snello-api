package io.snello.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@RegisterForReflection
public class Document {

    public String uuid;
    public String name;
    public String original_name;
    public String path;
    public String formats;
    public String mimetype;
    public LocalDateTime creation_date;
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
        if (map.get("creation_date") instanceof java.sql.Date) {
            document.creation_date = ((java.sql.Date) map.get("creation_date")).toLocalDate().atStartOfDay();
        }
        if (map.get("creation_date") instanceof LocalDateTime) {
            document.creation_date = (LocalDateTime) map.get("creation_date");
        }
        if (map.get("creation_date") instanceof LocalDate) {
            document.creation_date = ((LocalDate) map.get("creation_date")).atStartOfDay();
        }
        if (map.get("creation_date") instanceof java.sql.Timestamp) {
            document.creation_date = ((java.sql.Timestamp) map.get("creation_date")).toLocalDateTime();
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
        if (map.get("formats") instanceof String) {
            document.formats = (String) map.get("formats");
        }

        return document;
    }
}
