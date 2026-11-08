package io.snello.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.util.MapReads;
import io.snello.util.MapWrites;

import java.util.Map;

@RegisterForReflection
public class Link {
    /*
    - name  - es. languages
	- labels - es. IT, EN, FRA, DE
	- metadata_name - es news
	- metadata_key - es. news.uuid
	- metadata_searchable_field - es news.title
	- metadata_lock_field - es news.lang=label
	=> verra' generata il metadato
	- metadata_generated_uuid - es languages.uuid=label
     */
    public String name;
    public String labels;
    public String metadata_name;
    public String metadata_key;
    public String metadata_searchable_field;
    public String metadata_lock_field;
    public String metadata_generated_uuid;

    /*
    - uuid - 123sed12
	- name - es. LANGUAGES
	- it - es 12345
	- en - es 23456
	- fra -es 45678
	- de - es 98764
	- uuids - es 12345,23456,45678,98764
        => /api/links?it=12345&name=LANGUAGES
        => /api/links?uuids_cnt=12345&name=LANGUAGES
     */
    public boolean created;

    public Link() {
    }

    public Link(Map<String, Object> map) {
        fromMap(map, this);
    }

    public Link fromMap(Map<String, Object> map, Link link) {
        if (map == null || link == null) {
            return link;
        }
        link.name = MapReads.str(map, "name");
        link.labels = MapReads.str(map, "labels");
        link.metadata_name = MapReads.str(map, "metadata_name");
        link.metadata_key = MapReads.str(map, "metadata_key");
        link.metadata_searchable_field = MapReads.str(map, "metadata_searchable_field");
        link.metadata_lock_field = MapReads.str(map, "metadata_lock_field");
        link.metadata_generated_uuid = MapReads.str(map, "metadata_generated_uuid");
        link.created = MapReads.bool(map, "created", false);
        return link;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = MapWrites.map();
        MapWrites.put(map, "name", name);
        MapWrites.put(map, "labels", labels);
        MapWrites.put(map, "metadata_name", metadata_name);
        MapWrites.put(map, "metadata_key", metadata_key);
        MapWrites.put(map, "metadata_searchable_field", metadata_searchable_field);
        MapWrites.put(map, "metadata_lock_field", metadata_lock_field);
        MapWrites.put(map, "metadata_generated_uuid", metadata_generated_uuid);
        MapWrites.putBool(map, "created", created);
        return map;
    }

    @Override
    public String toString() {
        return "Link{" +
                "name='" + name + '\'' +
                ", labels='" + labels + '\'' +
                ", metadata_name='" + metadata_name + '\'' +
                ", metadata_key='" + metadata_key + '\'' +
                ", metadata_searchable_field='" + metadata_searchable_field + '\'' +
                ", metadata_lock_field='" + metadata_lock_field + '\'' +
                ", metadata_generated_uuid='" + metadata_generated_uuid + '\'' +
                ", created='" + created + '\'' +
                '}';
    }
}
