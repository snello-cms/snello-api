package io.snello.service.ai;

import io.quarkus.logging.Log;

import java.util.Map;

/**
 * The ai_* tables declare some optional text columns NOT NULL (no default), while the REST CRUD writes the request map
 * as-is: a client that leaves e.g. a project's roles empty would get a constraint violation. These helpers store ""
 * instead, matching the "" defaults of the model classes' toMap().
 */
public final class AiRowDefaults {

    private AiRowDefaults() {
    }

    /** Insert: missing or null optional columns become "". */
    public static void blankOnInsert(String table, Map<String, Object> row, String... columns) {
        for (String column : columns) {
            if (row.get(column) == null) {
                Log.debugf("[ai-row] %s insert: %s null/missing -> \"\"", table, column);
                row.put(column, "");
            }
        }
    }

    /** Update (partial merge): only columns explicitly sent as null become ""; absent ones stay untouched. */
    public static void blankOnUpdate(String table, Map<String, Object> row, String... columns) {
        for (String column : columns) {
            if (row.containsKey(column) && row.get(column) == null) {
                Log.debugf("[ai-row] %s update: %s null -> \"\"", table, column);
                row.put(column, "");
            }
        }
    }
}
