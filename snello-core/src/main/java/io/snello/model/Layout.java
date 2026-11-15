package io.snello.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.util.MapReads;
import io.snello.util.MapWrites;

import java.util.Map;
import java.util.Set;

/**
 * SPA page layout for a metadata (list / edit / view). Identity and library filters live in columns;
 * the page body is {@link #json_data} only (no duplicated name / type / metadata).
 */
@RegisterForReflection
public class Layout {

    public static final String LIST = "list";
    public static final String EDIT = "edit";
    public static final String VIEW = "view";
    public static final Set<String> PAGE_TYPES = Set.of(LIST, EDIT, VIEW);

    public static final String KIND_LIST = "list";
    public static final String KIND_WIZARD = "wizard";

    public String uuid;
    public String name;
    public String page_type;
    public String metadata_uuid;
    public String metadata_name;
    public String json_data;
    public String updated_at;

    public Layout() {
    }

    public Layout(Map<String, Object> map) {
        fromMap(map, this);
    }

    public Layout fromMap(Map<String, Object> map, Layout target) {
        if (map == null || target == null) {
            return target;
        }
        target.uuid = MapReads.str(map, "uuid");
        target.name = MapReads.str(map, "name");
        target.page_type = MapReads.str(map, "page_type");
        target.metadata_uuid = MapReads.str(map, "metadata_uuid");
        target.metadata_name = MapReads.str(map, "metadata_name");
        target.json_data = MapReads.str(map, "json_data");
        target.updated_at = MapReads.str(map, "updated_at");
        return target;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = MapWrites.map();
        MapWrites.put(map, "uuid", uuid);
        MapWrites.put(map, "name", name);
        MapWrites.put(map, "page_type", page_type);
        MapWrites.put(map, "metadata_uuid", metadata_uuid);
        MapWrites.put(map, "metadata_name", metadata_name);
        MapWrites.put(map, "json_data", json_data);
        MapWrites.put(map, "updated_at", updated_at);
        return map;
    }

    /** Expected {@code json_data.kind} for a given {@code page_type}. */
    public static String expectedKind(String pageType) {
        if (LIST.equals(pageType)) {
            return KIND_LIST;
        }
        if (EDIT.equals(pageType) || VIEW.equals(pageType)) {
            return KIND_WIZARD;
        }
        return null;
    }

    @Override
    public String toString() {
        return "Layout{uuid='" + uuid + "', name='" + name + "', page_type='" + page_type
                + "', metadata_name='" + metadata_name + "'}";
    }
}
