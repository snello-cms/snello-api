package io.snello.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.util.MapReads;
import io.snello.util.MapWrites;

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
        fromMap(map, this);
    }

    public Document fromMap(Map<String, Object> map, Document document) {
        if (map == null || document == null) {
            return document;
        }
        document.uuid = MapReads.str(map, "uuid");
        document.name = MapReads.str(map, "name");
        document.original_name = MapReads.str(map, "original_name");
        document.path = MapReads.str(map, "path");
        document.formats = MapReads.str(map, "formats");
        document.mimetype = MapReads.str(map, "mimetype");
        document.creation_date = MapReads.localDateTime(map, "creation_date");
        document.size = MapReads.intVal(map, "size", 0);
        document.table_name = MapReads.str(map, "table_name");
        document.table_key = MapReads.str(map, "table_key");
        return document;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = MapWrites.map();
        MapWrites.put(map, "uuid", uuid);
        MapWrites.put(map, "name", name);
        MapWrites.put(map, "original_name", original_name);
        MapWrites.put(map, "path", path);
        MapWrites.put(map, "formats", formats);
        MapWrites.put(map, "mimetype", mimetype);
        MapWrites.put(map, "creation_date", creation_date);
        MapWrites.putInt(map, "size", size);
        MapWrites.put(map, "table_name", table_name);
        MapWrites.put(map, "table_key", table_key);
        return map;
    }
}
