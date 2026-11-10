package io.snello.service;

import io.quarkus.logging.Log;
import io.snello.api.service.JdbcRepository;
import io.snello.management.SnelloPlane;
import io.snello.model.*;
import io.snello.util.ActionUtils;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

import static io.snello.management.AppConstants.*;

/**
 * In-memory catalog of the system tables (metadatas, fielddefinitions, conditions, selectqueries, actions, ai_tools).
 * <p>
 * Every map is built off to the side and then published with a single volatile write, so concurrent readers
 * never observe a half-filled map. Two invalidation paths exist:
 * <ul>
 *     <li>control plane: CDI events ({@link EventService}) null the maps and they are lazily rebuilt;</li>
 *     <li>query plane: {@link MetadataRefreshJob} calls {@link #refreshIfChanged()}, which reloads the rows,
 *     computes a checksum ({@link #version()}) and swaps every map only when the checksum moved.</li>
 * </ul>
 * Facet loading per plane: the control plane loads full rows (query + UI + AI facets). The query plane loads only
 * the query-facet columns of {@code metadatas} / {@code fielddefinitions} ({@link #QUERY_METADATA_COLUMNS},
 * {@link #QUERY_FIELD_COLUMNS}) and skips {@code actions} / {@code ai_tools}, which only the write path and the AI
 * runtime use. UI / AI fields of the cached objects are therefore {@code null} on query nodes.
 */
@ApplicationScoped
public class MetadataService {

    private volatile Map<String, Metadata> metadataMap;
    private volatile Map<String, SelectQuery> selectqueryMap;
    private volatile Map<String, AiTool> aiToolsMap;
    private volatile Map<String, Action> actionsMap;
    private volatile Map<String, Map<String, FieldDefinition>> fielddefinitionsMap;
    private volatile Map<String, List<Condition>> conditionsMap;

    private volatile String version;

    /**
     * Must be called via the CDI bean (never by writing fields through a client proxy).
     * Nulls metadata + fielddefinition caches so the next accessor reloads from DB.
     */
    public void invalidateMetadataCaches() {
        this.metadataMap = null;
        this.fielddefinitionsMap = null;
    }

    /**
     * Must be called via the CDI bean (never by writing fields through a client proxy).
     * Nulls conditions / selectqueries / actions / ai_tools caches.
     */
    public void invalidateSecondaryCaches() {
        this.conditionsMap = null;
        this.selectqueryMap = null;
        this.aiToolsMap = null;
        this.actionsMap = null;
    }

    /** Query-facet columns of {@code metadatas}: everything {@link io.snello.model.pojo.EntityQueryView} needs. */
    public static final String QUERY_METADATA_COLUMNS = "uuid, table_name, table_key, table_key_type, table_key_addition, "
            + "select_fields, order_by, alias_table, alias_condition, api_protected, username_field";

    /** Query-facet columns of {@code fielddefinitions}: everything {@link io.snello.model.pojo.FieldQueryView} needs. */
    public static final String QUERY_FIELD_COLUMNS = "uuid, metadata_uuid, metadata_name, name, sql_type, default_value, "
            + "table_key, join_table_name, join_table_key, join_table_select_fields, searchable, search_condition, "
            + "search_field_name, view_index";

    @Inject
    JdbcRepository jdbcRepository;

    @ConfigProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, defaultValue = "true")
    boolean controlPlane;


    public MetadataService() {

    }

    public Metadata byUUid(String uuid) throws Exception {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        for (Metadata metadata : metadataMap().values()) {
            if (metadata != null && uuid.equals(metadata.uuid)) {
                return metadata;
            }
        }
        throw new Exception("metadata not existent!");
    }

    public Metadata createTableFromMetadataAndFields(Metadata metadata, List<FieldDefinition> fieldDefinitions) throws Exception {
        if (metadata == null) {
            throw new Exception("metadata is null");
        }
        if (jdbcRepository.verifyTable(metadata.table_name)) {
            throw new Exception("table already existent!");
        }
        if (metadata.creation_query == null) {
            Log.info("no creation query found in metedata object...i need to createTableFromMetadata...");
            if (fieldDefinitions == null || fieldDefinitions.size() == 0) {
                throw new Exception("selectQuery without fields: " + metadata.toString());
            }
            List<String> relatedTables = new ArrayList<>();
            List<Condition> conditions = new ArrayList<>();
            String sqlQuery = jdbcRepository.createTableSql(metadata, fieldDefinitions, relatedTables, conditions);
            jdbcRepository.executeQuery(sqlQuery);
            for (String qq : relatedTables) {
                jdbcRepository.executeQuery(qq);
            }
            for (Condition cc : conditions) {
                jdbcRepository.create(CONDITIONS, UUID, cc.toMap());
            }
        } else {
            Log.info("creation query foud in metedata object: " + metadata.creation_query);
            jdbcRepository.executeQuery(metadata.creation_query);
        }
        return metadata;
    }

    public Metadata createTableFromMetadata(String uuid) throws Exception {
        Metadata metadata = byUUid(uuid);
        // Always reload fields from DB: metadata import fires async cache resets that can race
        // with field inserts, leaving a stale empty map for the last tables (e.g. video_types).
        this.fielddefinitionsMap = null;
        Map<String, FieldDefinition> fields = fielddefinitionsMap().get(metadata.uuid);
        if (fields == null || fields.size() == 0) {
            throw new Exception("selectQuery without fields: " + metadata.toString());
        }
        return createTableFromMetadataAndFields(metadata, new ArrayList<>(fields.values()));
    }

    // ------------------------------------------------------------------ lazy accessors

    public Map<String, Map<String, FieldDefinition>> fielddefinitionsMap() throws Exception {
        Map<String, Map<String, FieldDefinition>> current = this.fielddefinitionsMap;
        if (current == null) {
            current = buildFielddefinitionsMap(loadFielddefinitionRows());
            this.fielddefinitionsMap = current;
        }
        return current;
    }

    public Map<String, Metadata> metadataMap() throws Exception {
        Map<String, Metadata> current = this.metadataMap;
        if (current == null) {
            current = buildMetadataMap(loadMetadataRows());
            this.metadataMap = current;
        }
        return current;
    }

    public Map<String, SelectQuery> selectqueryMap() throws Exception {
        Map<String, SelectQuery> current = this.selectqueryMap;
        if (current == null) {
            current = buildSelectqueryMap(loadSelectqueryRows());
            this.selectqueryMap = current;
        }
        return current;
    }

    public Map<String, AiTool> aiToolsMap() throws Exception {
        Map<String, AiTool> current = this.aiToolsMap;
        if (current == null) {
            current = buildAiToolsMap(loadAiToolRows());
            this.aiToolsMap = current;
        }
        return current;
    }

    public Map<String, Action> actionsMap() throws Exception {
        Map<String, Action> current = this.actionsMap;
        if (current == null) {
            current = buildActionsMap(loadActionRows());
            this.actionsMap = current;
        }
        return current;
    }

    public Map<String, List<Condition>> conditionsMap() throws Exception {
        Map<String, List<Condition>> current = this.conditionsMap;
        if (current == null) {
            current = buildConditionsMap(loadConditionRows());
            this.conditionsMap = current;
        }
        return current;
    }

    // ------------------------------------------------------------------ versioned snapshot (query plane)

    /**
     * Checksum of the last snapshot loaded by {@link #refreshIfChanged()}; {@code null} until the first refresh.
     */
    public String version() {
        return version;
    }

    /**
     * Reloads every system table, and swaps all maps at once if their content changed since the last call.
     *
     * @return {@code true} when a new snapshot was published
     */
    public synchronized boolean refreshIfChanged() throws Exception {
        List<Map<String, Object>> metadataRows = loadMetadataRows();
        List<Map<String, Object>> fieldRows = loadFielddefinitionRows();
        List<Map<String, Object>> conditionRows = loadConditionRows();
        List<Map<String, Object>> selectqueryRows = loadSelectqueryRows();
        List<Map<String, Object>> actionRows = loadActionRows();
        List<Map<String, Object>> aiToolRows = loadAiToolRows();

        String newVersion = checksum(metadataRows, fieldRows, conditionRows, selectqueryRows, actionRows, aiToolRows);
        String oldVersion = this.version;
        if (newVersion.equals(oldVersion)) {
            Log.debugf("metadata snapshot unchanged version=%s", newVersion);
            return false;
        }
        Log.infof("metadata snapshot changed oldVersion=%s newVersion=%s metadatas=%d fielddefinitions=%d conditions=%d selectqueries=%d actions=%d aiTools=%d",
                oldVersion, newVersion, size(metadataRows), size(fieldRows), size(conditionRows),
                size(selectqueryRows), size(actionRows), size(aiToolRows));

        Map<String, Metadata> newMetadataMap = buildMetadataMap(metadataRows);
        Map<String, Map<String, FieldDefinition>> newFieldMap = buildFielddefinitionsMap(fieldRows);
        Map<String, List<Condition>> newConditionsMap = buildConditionsMap(conditionRows);
        Map<String, SelectQuery> newSelectqueryMap = buildSelectqueryMap(selectqueryRows);
        Map<String, Action> newActionsMap = buildActionsMap(actionRows);
        Map<String, AiTool> newAiToolsMap = buildAiToolsMap(aiToolRows);

        this.metadataMap = newMetadataMap;
        this.fielddefinitionsMap = newFieldMap;
        this.conditionsMap = newConditionsMap;
        this.selectqueryMap = newSelectqueryMap;
        this.actionsMap = newActionsMap;
        this.aiToolsMap = newAiToolsMap;
        this.version = newVersion;
        return true;
    }

    @SafeVarargs
    static String checksum(List<Map<String, Object>>... tables) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (List<Map<String, Object>> rows : tables) {
            digest.update((byte) '|');
            if (rows == null) {
                continue;
            }
            for (Map<String, Object> row : rows) {
                // TreeMap: column order independent of the driver's map implementation
                digest.update(String.valueOf(new TreeMap<>(row)).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
        }
        return HexFormat.of().formatHex(digest.digest()).substring(0, 16);
    }

    private static int size(List<?> list) {
        return list == null ? 0 : list.size();
    }

    // ------------------------------------------------------------------ loaders

    private List<Map<String, Object>> loadMetadataRows() throws Exception {
        return controlPlane
                ? jdbcRepository.list(METADATAS, " table_name asc ")
                : loadProjected(METADATAS, QUERY_METADATA_COLUMNS, " table_name asc ");
    }

    private List<Map<String, Object>> loadFielddefinitionRows() throws Exception {
        return controlPlane
                ? jdbcRepository.list(FIELD_DEFINITIONS, " metadata_name asc, view_index asc ")
                : loadProjected(FIELD_DEFINITIONS, QUERY_FIELD_COLUMNS, " metadata_name asc, view_index asc ");
    }

    /**
     * Query-plane load of only the query-facet columns. A legacy schema missing one of them falls back to
     * {@code SELECT *} so the node still serves (the missing column then simply reads as null).
     */
    private List<Map<String, Object>> loadProjected(String table, String columns, String sort) throws Exception {
        try {
            List<Map<String, Object>> rows = jdbcRepository.list(table, columns, null, null, null, sort, 0, 0);
            Log.debugf("query plane: loaded %d %s rows with query-facet columns only", rows == null ? 0 : rows.size(), table);
            return rows;
        } catch (Exception e) {
            Log.warnf("query plane: projected load of %s failed (%s); falling back to SELECT *", table, e.getMessage());
            return jdbcRepository.list(table, sort);
        }
    }

    private List<Map<String, Object>> loadSelectqueryRows() throws Exception {
        return jdbcRepository.list(SELECT_QUERY, " query_name asc ");
    }

    private List<Map<String, Object>> loadAiToolRows() throws Exception {
        if (!controlPlane) {
            return List.of(); // AI runtime is not shipped on the query plane
        }
        return jdbcRepository.list(AI_TOOLS, " name asc ");
    }

    private List<Map<String, Object>> loadActionRows() throws Exception {
        if (!controlPlane) {
            return List.of(); // actions run on create/update/delete only, which the query plane does not serve
        }
        // not ordered by `condition`: it is a reserved word in MySQL (unquoted ORDER BY broke every action there) and
        // the rows are only indexed by (table, condition, phase) afterwards, so the SQL order is irrelevant.
        return jdbcRepository.list(ACTIONS, " metadata_name asc, phase asc ");
    }

    private List<Map<String, Object>> loadConditionRows() throws Exception {
        return jdbcRepository.list(CONDITIONS, " metadata_name asc ");
    }

    // ------------------------------------------------------------------ builders (pure)

    private static Map<String, Metadata> buildMetadataMap(List<Map<String, Object>> rows) {
        Map<String, Metadata> map = new TreeMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Metadata metadata = new Metadata(row);
                map.put(metadata.table_name, metadata);
            }
        }
        return map;
    }

    private static Map<String, Map<String, FieldDefinition>> buildFielddefinitionsMap(List<Map<String, Object>> rows) {
        Map<String, Map<String, FieldDefinition>> map = new TreeMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                FieldDefinition fieldDefinition = new FieldDefinition(row);
                map.computeIfAbsent(fieldDefinition.metadata_uuid, k -> new HashMap<>())
                        .put(fieldDefinition.uuid, fieldDefinition);
            }
        }
        return map;
    }

    private static Map<String, SelectQuery> buildSelectqueryMap(List<Map<String, Object>> rows) {
        Map<String, SelectQuery> map = new TreeMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                SelectQuery selectQuery = new SelectQuery(row);
                map.put(selectQuery.query_name, selectQuery);
            }
        }
        return map;
    }

    private static Map<String, AiTool> buildAiToolsMap(List<Map<String, Object>> rows) {
        Map<String, AiTool> map = new TreeMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                AiTool aiTool = new AiTool(row);
                map.put(aiTool.name, aiTool);
            }
        }
        return map;
    }

    private static Map<String, Action> buildActionsMap(List<Map<String, Object>> rows) {
        Map<String, Action> map = new TreeMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Action action = new Action(row);
                map.put(ActionUtils.actionKey(action.metadata_name, action.condition, action.phase), action);
            }
        }
        return map;
    }

    private static Map<String, List<Condition>> buildConditionsMap(List<Map<String, Object>> rows) {
        Map<String, List<Condition>> map = new TreeMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Condition condition = new Condition(row);
                map.computeIfAbsent(condition.metadata_name, k -> new ArrayList<>()).add(condition);
            }
        }
        return map;
    }

    // ------------------------------------------------------------------ lookups

    public Set<String> names() throws Exception {
        return metadataMap().keySet();
    }

    public Metadata metadata(String table) throws Exception {
        return metadataMap().get(table);
    }

    public List<FieldDefinition> fielddefinitions(String metadata_name) throws Exception {
        Metadata metadata = metadataMap().get(metadata_name);
        if (metadata == null) {
            return new ArrayList<>();
        }
        Map<String, FieldDefinition> defs = fielddefinitionsMap().get(metadata.uuid);
        if (defs == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(defs.values());
    }

    public List<Condition> conditions(String metadata_name) throws Exception {
        List<Condition> conds = conditionsMap().get(metadata_name);
        return conds == null ? new ArrayList<>() : conds;
    }

    public String initTableKey(String table, String table_key) throws Exception {
        if (metadataMap().containsKey(table)) {
            Metadata metadata = metadataMap().get(table);
            return metadata.table_key;
        }
        return table_key;
    }


}
