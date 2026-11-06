package io.snello.util;

import static io.snello.management.AppConstants.*;

public class MetadataUtils {


    /**
     * Names an entity may not take: every top-level {@code /api/<name>} owned by a system resource. An entity with
     * such a name would be shadowed by that resource's routes.
     */
    public static boolean isReserved(Object table_name) {
        if (!(table_name instanceof String name)) {
            return false;
        }
        switch (name) {
            case ACTIONS:
            case AUTH:
            case BACKUP:
            case CHAT:
            case CHAT_INTERACTIONS:
            case DRAFTS:
            case PAGES:
            case CONDITIONS:
            case DATALISTS:
            case DOCUMENTS:
            case FIELD_DEFINITIONS:
            case LINKS:
            case METADATAS:
            case PUBLIC_DATA:
            case SELECT_QUERY:
            case AI_TOOLS:
                return true;
            default:
                return false;
        }
    }
}
