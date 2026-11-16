package io.snello.util;

import io.snello.model.Metadata;
import io.snello.model.enums.KeyType;
import io.snello.service.ApiService;

import java.util.Map;

public class TableKeyUtils {


    public static boolean isSlug(Metadata metadata) {
        return KeyType.from(metadata.table_key_type) == KeyType.SLUG;
    }

    public static void generateUUid(Map<String, Object> map, Metadata metadata, ApiService apiService) throws Exception {
        KeyType type = KeyType.from(metadata.table_key_type);
        if (type == KeyType.UUID) {
            uuid(map, metadata);
        } else if (type == KeyType.SLUG) {
            slug(map, metadata, apiService);
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
                throw new jakarta.ws.rs.BadRequestException(metadata.table_key_addition
                        + " is required: the key of " + metadata.table_name + " is built from it");
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
