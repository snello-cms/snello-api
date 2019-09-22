package io.snello.util;

import io.snello.model.Metadata;
import io.snello.service.ApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class TableKeyUtils {

    static Logger logger = LoggerFactory.getLogger(TableKeyUtils.class);

    public static boolean isSlug(Metadata metadata) {
        switch (metadata.table_key_type) {
            case "slug":
                return true;
            default:
                return false;
        }
    }

    public static void generateUUid(Map<String, Object> map, Metadata metadata, ApiService apiService) throws Exception {
        switch (metadata.table_key_type) {
            case "uuid":
                uuid(map, metadata);
                return;
            case "slug":
                slug(map, metadata, apiService);
                return;
            default:
                return;
        }
    }

    public static void uuid(Map<String, Object> map, Metadata metadata) {
        String key = metadata.table_key;
        map.put(key, java.util.UUID.randomUUID().toString());
    }

    public static void slug(Map<String, Object> map, Metadata metadata, ApiService apiService) throws Exception {
        String key = metadata.table_key;
        if (metadata.table_key_addition != null) {
            String toSlug = (String) map.get(metadata.table_key_addition);
            if (toSlug != null)
                map.put(key, makeUniqueSlug(toSlug, metadata.table_name, metadata.table_key, apiService));
            else {
                throw new Exception("table_key_addition is empty!");
            }
        } else {
            //deve essere inserito liberamente
            map.put(key, (String) map.get(metadata.table_key));
        }
    }

    public static String makeUniqueSlug(String slug, String table, String table_key, ApiService apiService) throws Exception {
        String slugged = createSlug(slug);
        String keyNotUsed = slugged;
        boolean found = false;
        int i = 0;
        while (!found) {
            if (apiService.exist(table, table_key, keyNotUsed)) {
                i++;
                keyNotUsed = slugged + "-" + i;
            } else {
                return keyNotUsed;
            }
        }
        return "";
    }


    public static String createSlug(String field) {
        if (field == null)
            return null;
        field = field.trim().replaceAll("[^a-zA-Z0-9\\s]", "")
                .replaceAll("[\\s]", "-");
        return field.toLowerCase();
    }
}
