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
            case SCHEDULED_ACTIONS:
            case PRINT_TEMPLATES:
            case AI_TOOLS:
            case HISTORY_SESSION:
            case HISTORY_EVENT:
            case AI_PROJECT:
            case AI_SKILL:
            case AI_MCP_TOOL:
            case AI_UI_CARD:
            case AI_SERVICE_ACCOUNT:
            case "ai":
            case "agui":
                return true;
            default:
                return false;
        }
    }
}
