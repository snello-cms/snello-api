package io.snello.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

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
        super();
        fromMap(map, this);
    }

    public Link fromMap(Map<String, Object> map, Link link) {
        if (map.get("name") instanceof String) {
            link.name = (String) map.get("name");
        }
        if (map.get("labels") instanceof String) {
            link.labels = (String) map.get("labels");
        }
        if (map.get("metadata_name") instanceof String) {
            link.metadata_name = (String) map.get("metadata_name");
        }
        if (map.get("metadata_key") instanceof String) {
            link.metadata_key = (String) map.get("metadata_key");
        }

        if (map.get("metadata_searchable_field") instanceof String) {
            link.metadata_searchable_field = (String) map.get("metadata_searchable_field");
        }
        if (map.get("created") instanceof Boolean) {
            link.created = (Boolean) map.get("created");
        }
        return link;
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
