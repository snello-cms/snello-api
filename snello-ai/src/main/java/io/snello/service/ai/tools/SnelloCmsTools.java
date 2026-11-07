package io.snello.service.ai.tools;

import dev.langchain4j.agent.tool.Tool;
import tools.jackson.databind.json.JsonMapper;
import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import io.snello.model.FieldDefinition;
import io.snello.model.Metadata;
import io.snello.model.facet.EntityAiFacet;
import io.snello.model.facet.FieldAiFacet;
import io.snello.model.SearchResult;
import io.snello.model.pojo.DraftResult;
import io.snello.model.pojo.EntityDraft;
import io.snello.service.ApiService;
import io.snello.service.DraftService;
import io.snello.service.MetadataService;
import io.snello.service.ai.wss.ActiveAguiRun;
import io.snello.service.ai.wss.AguiServiceWss;
import jakarta.enterprise.context.ApplicationScoped;
import io.snello.util.FieldTypeUtils;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedHashMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static io.snello.management.AppConstants.*;

/**
 * LangChain4j Tools that expose snello.io CMS APIs to the AI assistant.
 * <p>
 * Tools are grouped into two areas:
 * <ul>
 * <li><b>Schema / Metadata</b>: read entity schemas and list available
 * entities.</li>
 * <li><b>Data</b>: list, fetch, create and update records.</li>
 * </ul>
 * 
 */
@ApplicationScoped
public class SnelloCmsTools {

    public static final String LOAD_MORE_NO_PREVIOUS_CONTEXT = "__LOAD_MORE_NO_PREVIOUS_CONTEXT__";
    public static final String LOAD_MORE_NO_MORE_RECORDS = "__LOAD_MORE_NO_MORE_RECORDS__";

    @Inject
    ApiService apiService;

    @Inject
    MetadataService metadataService;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    JsonMapper objectMapper;

    @Inject
    ActiveAguiRun activeAguiRun;

    @Inject
    AiPaginationContextStore aiPaginationContextStore;

    @Inject
    DraftService draftService;

    private static final String DRAFT_SHAPE = "Draft JSON: {\"table_name\":\"products\","
            + "\"ai\":{\"description\":\"required\",\"summary\":\"...\",\"synonyms\":[\"items\"]},"
            + "\"query\":{\"table_key_type\":\"uuid|slug|autoincrement|userdefined\",\"table_key_addition\":\"slug source field\","
            + "\"api_protected\":false,\"username_field\":\"owner field\"},"
            + "\"fields\":[{\"name\":\"price\",\"ai\":{\"description\":\"...\",\"semantic_type\":\"money\"},"
            + "\"query\":{\"searchable\":true,\"join_table_name\":\"entity, for reference(s)\"},"
            + "\"ui\":{\"options\":\"a,b,c for enum\"}}]}. "
            + "semantic_type: text, long_text, html, integer, decimal, money, boolean, date, datetime, time, email, "
            + "password, url, enum, image, media, tags, reference, references. "
            + "Do not declare the uuid key as a field; UI widgets are derived automatically.";

    // ── Schema / Metadata tools ───────────────────────────────────────────────

    /**
     * Maps FieldDefinition type and input_type to a unified, simplified type for AI context.
     * Normalizes: string, number, decimal, date, datetime
     * For join fields, returns "text (join: foreignKeyFieldName)" or "text (multijoin: comma-separated uuids)"
     */
    // Removed the private method getUnifiedFieldType
    // Use FieldTypeUtils.getUnifiedFieldType(fd) instead where necessary

    @Tool("Returns the list of all available entity names (table names) registered in the CMS")
    public String listEntities() {
        try {
            Map<String, Metadata> metadataMap = metadataService.metadataMap();
            List<String> entitiesWithDescription = metadataMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .filter(entry -> entry.getValue() != null && entry.getValue().aiFacet().tool_visible)
                    .map(entry -> describeEntity(entry.getKey(), entry.getValue().aiFacet()))
                    .toList();
            Log.infof("listEntities: visible=%d hidden=%d", entitiesWithDescription.size(),
                    metadataMap.size() - entitiesWithDescription.size());
            return "Available entities with description: " + String.join(", ", entitiesWithDescription);
        } catch (Exception e) {
            Log.error("listEntities error", e);
            return "Error retrieving entity list: " + e.getMessage();
        }
    }

    private static String describeEntity(String entityName, EntityAiFacet ai) {
        StringBuilder sb = new StringBuilder(entityName);
        if (ai.hasDescription()) {
            sb.append(" (").append(ai.description).append(")");
        }
        if (ai.synonyms != null && !ai.synonyms.isEmpty()) {
            sb.append(" [also called: ").append(String.join(", ", ai.synonyms)).append("]");
        }
        return sb.toString();
    }

    /**
     * Metadata of an entity the AI may touch; {@code null} both when it does not exist and when its
     * {@code ai_profile.tool_visible} is false, so hidden entities are indistinguishable from missing ones.
     */
    private Metadata toolVisibleMetadata(String entityName) throws Exception {
        Metadata metadata = apiService.metadata(entityName);
        if (metadata == null) {
            return null;
        }
        if (!metadata.aiFacet().tool_visible) {
            Log.infof("AI tool access to hidden entity=%s refused (tool_visible=false)", entityName);
            return null;
        }
        return metadata;
    }

    private static String notFound(String entityName) {
        return "Entity '" + entityName + "' not found. Use listEntities() to see available entities.";
    }

    @Tool("Returns the schema (fields, types and constraints) of a CMS entity. " +
            "Call this before any data entry operation to know which fields are required and their types.")
    public String getEntitySchema(String entityName) {
        try {
            if (toolVisibleMetadata(entityName) == null) {
                return notFound(entityName);
            }
            Metadata metadata = apiService.metadataWithFields(entityName);
            if (metadata == null) {
                return notFound(entityName);
            }
            List<FieldDefinition> fields = metadata.fields;
            if (fields == null || fields.isEmpty()) {
                return "Entity '" + entityName + "' has no fields defined.";
            }
            StringBuilder sb = new StringBuilder("Schema for entity '").append(entityName).append("':\n");
            EntityAiFacet entityAi = metadata.aiFacet();
            if (entityAi.hasDescription()) {
                sb.append("- Description: ").append(entityAi.description).append("\n");
            }
            if (entityAi.summary != null && !entityAi.summary.isBlank()) {
                sb.append("- Summary: ").append(entityAi.summary).append("\n");
            }
            if (entityAi.synonyms != null && !entityAi.synonyms.isEmpty()) {
                sb.append("- Also called: ").append(String.join(", ", entityAi.synonyms)).append("\n");
            }
            sb.append("- Primary key: ").append(metadata.table_key).append(" (").append(metadata.table_key_type)
                    .append(")\n");
            for (FieldDefinition fd : fields) {
                sb.append("- ").append(fd.name);
                if (fd.description != null && !fd.description.isBlank()) {
                    sb.append(" | description: ").append(fd.description);
                }
                String unifiedType = FieldTypeUtils.getUnifiedFieldType(fd);
                if (unifiedType != null && !unifiedType.isBlank()) {
                    sb.append(" | type: ").append(unifiedType);
                }

                if (fd.options != null && !fd.options.isBlank()) {
                    sb.append(" | options: ").append(fd.options);
                }
                FieldAiFacet fieldAi = fd.aiFacet();
                if (fieldAi.semantic_type != null && !fieldAi.semantic_type.isBlank()) {
                    sb.append(" | meaning: ").append(fieldAi.semantic_type);
                }
                if (fieldAi.ai_hint != null && !fieldAi.ai_hint.isBlank()) {
                    sb.append(" | hint: ").append(fieldAi.ai_hint);
                }
                if (fieldAi.examples != null && !fieldAi.examples.isEmpty()) {
                    sb.append(" | examples: ").append(objectMapper.writeValueAsString(fieldAi.examples));
                }
                sb.append("\n");
            }
            if (entityAi.examples != null && !entityAi.examples.isEmpty()) {
                sb.append("Example payloads for createRecord / updateRecord:\n");
                for (Map<String, Object> example : entityAi.examples) {
                    sb.append("- ").append(objectMapper.writeValueAsString(example)).append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            Log.error("getEntitySchema error for: " + entityName, e);
            return "Error retrieving schema for '" + entityName + "': " + e.getMessage();
        }
    }

    // ── Authoring tools (typed drafts) ────────────────────────────────────────

    @Tool("Validates a proposed NEW entity without writing anything (dry run). Returns errors, warnings, the "
            + "derived metadata / field rows and the CREATE TABLE preview. Always call this before applyEntity "
            + "and fix every error. " + DRAFT_SHAPE)
    public String proposeEntity(String draftJson) {
        try {
            EntityDraft draft = objectMapper.readValue(draftJson, EntityDraft.class);
            DraftResult result = draftService.validate(draft);
            Log.infof("AI proposeEntity table=%s valid=%s", draft.table_name(), result.valid());
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            Log.error("proposeEntity error", e);
            return "Error: the draft is not valid JSON for the expected shape: " + e.getMessage() + ". " + DRAFT_SHAPE;
        }
    }

    @Tool("Creates a NEW entity from a draft that proposeEntity reported as valid: stores its metadata and fields "
            + "and, when createTable is true, creates the database table. Admin or Manager only. " + DRAFT_SHAPE)
    public String applyEntity(String draftJson, boolean createTable) {
        checkAuthenticated();
        if (!isAdminOrManager()) {
            throw new SecurityException("Only Admin or Manager can create entities.");
        }
        try {
            EntityDraft draft = objectMapper.readValue(draftJson, EntityDraft.class);
            DraftResult result = draftService.apply(draft, createTable);
            Log.infof("AI applyEntity table=%s applied=%s table_created=%s by=%s", draft.table_name(),
                    result.applied(), result.table_created(), currentUsernameOrNull());
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            Log.error("applyEntity error", e);
            return "Error applying entity: " + e.getMessage();
        }
    }

    // ── Data tools ────────────────────────────────────────────────────────────

    @Tool("Lists records from a CMS entity with filters and pagination. " +
            "Arguments: entityName, params, limit, start. " +
            "params is a simple HashMap<String, String> and is converted internally to a multivalued map. " +
            "Use params for filters with the same syntax as REST query params: " +
            "field=value (exact), field_ne, field_lt, field_lte, field_gt, field_gte, " +
            "field_contains/field_like, field_ilike, field_containss, field_rlike, field_llike, field_ncontains, " +
            "field_in=a,b,c, field_nn, field_inn, field_nie, field_ie. " +
            "Examples: params={\"status\":\"published\",\"price_gte\": 50,\"title_ilike\":\"java\"}. " +
            "Returns a SearchResult JSON with load-more metadata (hasMore, remaining, nextStart). " +
            "Maximum limit is 10.")
    public String listRecords(String entityName, HashMap<String, String> params, int limit, int start) {
        try {
            Metadata metadata = toolVisibleMetadata(entityName);
            if (metadata == null) {
                return notFound(entityName);
            }

            if (limit <= 0) {
                limit = 10;
            }
            if (limit > 10) {
                limit = 10;
            }
            if (start < 0) {
                start = 0;
            }

            MultivaluedHashMap<String, String> effectiveParams = new MultivaluedHashMap<>();
            if (params != null) {
                params.forEach((key, value) -> effectiveParams.putSingle(key, value == null ? "" : value));
            }

            if (metadata.api_protected) {
                if (!isAdminOrManager()) {
                    checkAuthenticated();
                    effectiveParams.put(metadata.username_field, List.of(currentUsername()));
                } else {
                    Log.info("admin or manager");
                }
            }

            long count = apiService.count(entityName, effectiveParams);
            List<Map<String, Object>> records = apiService.list(entityName, effectiveParams, null, limit, start);
            int currentSize = records == null ? 0 : records.size();
            long remaining = Math.max(0L, count - (start + currentSize));
            boolean hasMore = remaining > 0;
            int nextStart = start + currentSize;

            String conversationId = paginationKey();
            if (conversationId != null) {
                AiPaginationContextStore.LastSearchContext lastSearchContext = AiPaginationContextStore.LastSearchContext
                        .of(entityName, params, limit, nextStart, hasMore);
                aiPaginationContextStore.save(conversationId, lastSearchContext);
            }

            SearchResult result = new SearchResult(count, count, start, limit, hasMore, remaining, nextStart, records);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            Log.error("listRecords error for: " + entityName, e);
            return "Error listing records for '" + entityName + "': " + e.getMessage();
        }
    }

    private String paginationKey() {
        if (activeAguiRun != null && activeAguiRun.isBound()) {
            return AguiServiceWss.memoryId(activeAguiRun.userId(), activeAguiRun.threadId());
        }
        return null;
    }

    private String currentUsernameOrNull() {
        if (securityIdentity == null || securityIdentity.isAnonymous() || securityIdentity.getPrincipal() == null) {
            return null;
        }
        return securityIdentity.getPrincipal().getName();
    }

    @Tool("Loads the next page from the last listRecords call in the same conversation. " +
            "Use this when the user says 'load more data', 'carica altri dati', 'next page', or 'more'. " +
            "It automatically reuses the same entity and filters from the previous search.")
    public String loadMoreRecords() {
        try {
            String conversationId = paginationKey();
            AiPaginationContextStore.LastSearchContext context = aiPaginationContextStore.get(conversationId);
            if (context == null) {
                return LOAD_MORE_NO_PREVIOUS_CONTEXT;
            }
            if (!context.hasMore) {
                return LOAD_MORE_NO_MORE_RECORDS;
            }

            HashMap<String, String> paramsCopy = context.params == null ? new HashMap<>() : new HashMap<>(context.params);
            return listRecords(context.entityName, paramsCopy, context.limit, context.nextStart);
        } catch (Exception e) {
            Log.error("loadMoreRecords error", e);
            return "Error loading more records: " + e.getMessage();
        }
    }

    @Tool("Explains how to build filters for listRecords params using REST query-param syntax.")
    public String explainFilters() {
        StringBuilder sb = new StringBuilder();
        sb.append("Filter guide for listRecords(entityName, params, limit, start):\n\n");
        sb.append("1) Pagination and sorting\n");
        sb.append("- limit and start are supported\n");
        sb.append("- maximum limit is 10 (if limit > 10, limit is forced to 10)\n");
        sb.append("- Use hasMore/remaining/nextStart in response to ask for additional data\n");
        sb.append("- sort is supported via params['_sort'] with format field:asc|desc\n\n");

        sb.append("2) params format\n");
        sb.append("- params is a simple map: field -> single value\n");
        sb.append("- Example: {status:published, price_gte:50, title_ilike:java}\n\n");

        sb.append("3) Supported filter suffixes\n");
        sb.append("- Equality: field=value\n");
        sb.append("- Comparison: _ne, _lt, _lte, _gt, _gte\n");
        sb.append("- Text: _contains, _like, _ilike, _containss, _rlike, _llike, _ncontains\n");
        sb.append("- Set: _in (comma-separated values)\n");
        sb.append("- Null checks: _nn (IS NOT NULL), _inn (IS NULL)\n");
        sb.append("- Empty checks: _nie (not empty), _ie (empty)\n\n");

        sb.append("4) Examples\n");
        sb.append("- status exact match: {status:published}\n");
        sb.append("- numeric range: {price_gte:50, price_lte:200}\n");
        sb.append("- contains case-insensitive: {title_ilike:spring}\n");
        sb.append("- IN list: {status_in:[draft,published,review]}\n");
        sb.append("- pagination call: limit=10, start=20\n");
        sb.append("- if hasMore=true, client can show: 'Carica altri dati'\n\n");

        sb.append("All filters are combined with logical AND.");
        return sb.toString();
    }

    @Tool("Fetches a single record from a CMS entity by its UUID.")
    public String fetchRecord(String entityName, String uuid) {
        checkAuthenticated();
        try {
            Metadata metadata = toolVisibleMetadata(entityName);
            if (metadata == null) {
                return notFound(entityName);
            }
            String tableKey = apiService.table_key(entityName);
            Map<String, Object> record = apiService.fetch(null, entityName, uuid, tableKey);
            String notFoundRecord = "Record with uuid '" + uuid + "' not found in entity '" + entityName + "'.";
            if (record == null) {
                return notFoundRecord;
            }
            if (metadata.api_protected && metadata.username_field != null && !metadata.username_field.isBlank()
                    && !isAdminOrManager()
                    && !java.util.Objects.equals(record.get(metadata.username_field), currentUsername())) {
                // same answer as a missing row: do not reveal other owners' records
                Log.infof("fetchRecord entity=%s uuid=%s: owner mismatch, reported as not found", entityName, uuid);
                return notFoundRecord;
            }
            return objectMapper.writeValueAsString(record);
        } catch (Exception e) {
            Log.error("fetchRecord error for: " + entityName + " uuid: " + uuid, e);
            return "Error fetching record: " + e.getMessage();
        }
    }

    @Tool("Creates a new record in a CMS entity. " +
            "Provide the entity name and the record data as a JSON string. " +
            "Call getEntitySchema first to know the required fields.")
    public String createRecord(String entityName, String jsonData) {
        checkAuthenticated();
        checkWritePermission(entityName);
        try {
            if (toolVisibleMetadata(entityName) == null) {
                return notFound(entityName);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(jsonData, Map.class);
            String tableKey = apiService.table_key(entityName);
            Map<String, Object> created = apiService.create(entityName, data, tableKey);
            return "Record created successfully: " + objectMapper.writeValueAsString(created);
        } catch (Exception e) {
            Log.error("createRecord error for: " + entityName, e);
            return "Error creating record in '" + entityName + "': " + e.getMessage();
        }
    }

    @Tool("Updates an existing record in a CMS entity. " +
            "Provide the entity name, the record UUID and the fields to update as a JSON string.")
    public String updateRecord(String entityName, String uuid, String jsonData) {
        checkAuthenticated();
        checkWritePermission(entityName);
        try {
            if (toolVisibleMetadata(entityName) == null) {
                return notFound(entityName);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(jsonData, Map.class);
            String tableKey = apiService.table_key(entityName);
            Map<String, Object> updated = apiService.merge(entityName, data, uuid, tableKey);
            return "Record updated successfully: " + objectMapper.writeValueAsString(updated);
        } catch (Exception e) {
            Log.error("updateRecord error for: " + entityName + " uuid: " + uuid, e);
            return "Error updating record: " + e.getMessage();
        }
    }

    // ── Security helpers ──────────────────────────────────────────────────────

    private void checkAuthenticated() {
        if (securityIdentity.isAnonymous()) {
            throw new SecurityException("Authentication required to access CMS data.");
        }
    }

    private void checkWritePermission(String entityName) {
        boolean isAdmin = securityIdentity.hasRole("Admin") || securityIdentity.hasRole("Manager");
        if (!isAdmin) {
            throw new SecurityException(
                    "You do not have permission to write data to entity '" + entityName + "'.");
        }
    }

    private boolean isAdminOrManager() {
        return securityIdentity.hasRole("admin") || securityIdentity.hasRole("Admin")
                || securityIdentity.hasRole("manager") || securityIdentity.hasRole("Manager");
    }

    private String currentUsername() {
        if (securityIdentity.getPrincipal() == null) {
            throw new SecurityException("Authentication required to access CMS data.");
        }
        return securityIdentity.getPrincipal().getName();
    }
}
