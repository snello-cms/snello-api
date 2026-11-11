package io.snello.service;

import io.quarkus.logging.Log;
import io.snello.api.service.JdbcRepository;
import io.snello.model.Metadata;
import io.snello.model.pojo.ConfigImportResult;
import io.snello.util.MetadataUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static io.snello.management.AppConstants.ACTIONS;
import static io.snello.management.AppConstants.CONDITIONS;
import static io.snello.management.AppConstants.PRINT_TEMPLATES;
import static io.snello.management.AppConstants.SCHEDULED_ACTIONS;
import static io.snello.management.AppConstants.SELECT_QUERY;
import static io.snello.management.AppConstants.UUID;

/**
 * Config part of the metadata export/import: actions, conditions, select queries, scheduled actions and print
 * templates travel under {@code "config"} next to {@code "metadatas"} (in the JSON body, or in metadatas.json of
 * the ZIP).
 * <pre>
 * export body flags: "actions", "conditions", "select_queries", "scheduled_actions", "print_templates" (booleans)
 * output:            "config": {"actions": [rows], "conditions": [rows], "selectqueries": [rows],
 *                               "scheduledactions": [rows], "printtemplates": [rows]}
 * </pre>
 * Actions and conditions belong to a metadata: they follow the exported metadatas, or export all rows when no
 * metadata is exported (a config-only export, to update a project that already has the metadatas). The other
 * three always export all rows.
 * <p>
 * Import overwrites by natural key (see {@link #NATURAL_KEYS}): an existing row keeps its uuid and gets the
 * exported values, a missing row is inserted. Scheduled actions are imported disabled, so a copied job never
 * starts running in the target environment by surprise. Row errors are collected, not thrown.
 */
@ApplicationScoped
public class ConfigTransferService {

    public static final String CONFIG_KEY = "config";

    /** Export body flag → config table, in import order (none depends on another, conditions need metadatas). */
    public static final Map<String, String> FLAGS = linked(
            "actions", ACTIONS,
            "conditions", CONDITIONS,
            "select_queries", SELECT_QUERY,
            "scheduled_actions", SCHEDULED_ACTIONS,
            "print_templates", PRINT_TEMPLATES);

    /** Columns copied on import: unknown keys (a newer or older export) are dropped instead of failing the insert. */
    static final Map<String, List<String>> COLUMNS = Map.of(
            ACTIONS, List.of(UUID, "name", "description", "metadata_name", "condition", "phase", "body"),
            CONDITIONS, List.of(UUID, "metadata_uuid", "metadata_name", "separator", "condition", "query_params",
                    "sub_query", "metadata_multijoin_uuid"),
            SELECT_QUERY, List.of(UUID, "query_name", "with_params", "select_query", "select_query_count",
                    "api_protected", "username_field"),
            SCHEDULED_ACTIONS, List.of(UUID, "name", "description", "frequency", "enabled", "body"),
            PRINT_TEMPLATES, List.of(UUID, "name", "description", "export_type", "sql_query", "html_body",
                    "template_file", "template_file_name", "api_protected", "username_field"));

    /** How an imported row finds the row it overwrites. Conditions have no name: their uuid is the identity. */
    static final Map<String, List<String>> NATURAL_KEYS = Map.of(
            ACTIONS, List.of("name", "metadata_name"),
            CONDITIONS, List.of(UUID),
            SELECT_QUERY, List.of("query_name"),
            SCHEDULED_ACTIONS, List.of("name"),
            PRINT_TEMPLATES, List.of("name"));

    private static final Pattern PRINT_TEMPLATE_NAME = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Set<String> PRINT_TYPES = Set.of("pdf", "html", "csv", "xlsx");

    @Inject
    JdbcRepository jdbcRepository;

    @Inject
    ApiService apiService;

    @Inject
    MetadataService metadataService;

    // ------------------------------------------------------------------ export

    /** True when the export body asks for at least one config table. */
    public static boolean anyRequested(Map<String, Object> body) {
        return body != null && FLAGS.keySet().stream().anyMatch(flag -> Boolean.TRUE.equals(body.get(flag)));
    }

    /**
     * @param body       export body (flags)
     * @param tableNames table names of the exported metadatas: actions and conditions are limited to them (empty: all)
     * @return {@code {table: [rows]}} for each requested table, empty when none is requested
     */
    public Map<String, Object> export(Map<String, Object> body, Collection<String> tableNames) throws Exception {
        Map<String, Object> config = new LinkedHashMap<>();
        for (Map.Entry<String, String> flag : FLAGS.entrySet()) {
            if (body == null || !Boolean.TRUE.equals(body.get(flag.getKey()))) {
                continue;
            }
            String table = flag.getValue();
            List<Map<String, Object>> rows = jdbcRepository.list("SELECT * FROM " + table);
            boolean scoped = (table.equals(ACTIONS) || table.equals(CONDITIONS)) && !tableNames.isEmpty();
            if (scoped) {
                rows = rows.stream().filter(r -> tableNames.contains(String.valueOf(r.get("metadata_name")))).toList();
            }
            List<Map<String, Object>> out = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                out.add(pick(table, row));
            }
            Log.infof("config export: %s -> %d row(s)%s", table, out.size(),
                    scoped ? " (of the exported metadatas)" : " (all)");
            config.put(table, out);
        }
        return config;
    }

    // ------------------------------------------------------------------ import

    /** True when the import body carries a non-empty {@code config} object. */
    public static boolean hasConfig(Map<String, Object> body) {
        return body != null && body.get(CONFIG_KEY) instanceof Map<?, ?> m && !m.isEmpty();
    }

    /**
     * Imports every config table present in {@code body.config}. Run after the metadatas: conditions point to
     * them by name.
     *
     * @return {@code {table: ConfigImportResult}}
     */
    public Map<String, ConfigImportResult> importConfig(Map<String, Object> body) {
        Map<String, ConfigImportResult> results = new LinkedHashMap<>();
        if (!(body.get(CONFIG_KEY) instanceof Map<?, ?> config)) {
            return results;
        }
        // metadatas imported a moment ago refresh the cache asynchronously: conditions must see them now
        metadataService.invalidateMetadataCaches();
        for (String table : FLAGS.values()) {
            if (!(config.get(table) instanceof List<?> rows)) {
                continue;
            }
            ConfigImportResult result = new ConfigImportResult();
            Log.infof("config import: %s, %d row(s)", table, rows.size());
            for (Object raw : rows) {
                if (!(raw instanceof Map<?, ?> rawMap)) {
                    result.errors.add(table + ": row is not an object, skipped");
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> row = pick(table, (Map<String, Object>) rawMap);
                try {
                    importRow(table, row, result);
                } catch (Exception e) {
                    Log.errorf(e, "config import: %s row %s failed", table, describe(table, row));
                    result.errors.add(table + " " + describe(table, row) + ": " + e.getMessage());
                }
            }
            Log.infof("config import: %s created=%d updated=%d errors=%d", table, result.created, result.updated,
                    result.errors.size());
            results.put(table, result);
        }
        // conditions, select queries and actions are cached by MetadataService: reload them from the tables
        metadataService.invalidateSecondaryCaches();
        return results;
    }

    private void importRow(String table, Map<String, Object> row, ConfigImportResult result) throws Exception {
        String invalid = prepare(table, row);
        if (invalid != null) {
            Log.warnf("config import: %s row %s skipped: %s", table, describe(table, row), invalid);
            result.errors.add(table + " " + describe(table, row) + ": " + invalid + ", skipped");
            return;
        }
        Map<String, Object> existing = findExisting(table, row);
        if (existing != null) {
            String uuid = String.valueOf(existing.get(UUID));
            row.put(UUID, uuid); // the target keeps its own identity
            Log.infof("config import: %s %s exists (uuid=%s), overwriting", table, describe(table, row), uuid);
            jdbcRepository.update(table, UUID, row, uuid);
            result.updated++;
            return;
        }
        Object uuid = row.get(UUID);
        if (uuid == null || uuid.toString().isBlank() || jdbcRepository.exist(table, UUID, uuid.toString())) {
            // the exported uuid is missing, or used by a row with a different natural key
            row.put(UUID, java.util.UUID.randomUUID().toString());
        }
        Log.infof("config import: %s %s new, uuid=%s", table, describe(table, row), row.get(UUID));
        jdbcRepository.create(table, UUID, row);
        result.created++;
    }

    /** Fills defaults and validates. @return the reason the row cannot be imported, or null. */
    private String prepare(String table, Map<String, Object> row) {
        for (String key : NATURAL_KEYS.get(table)) {
            Object value = row.get(key);
            if (value == null || value.toString().isBlank()) {
                return key + " is missing";
            }
        }
        switch (table) {
            case ACTIONS -> {
                if (row.get("description") == null) { // NOT NULL in the schema, optional in the editor
                    row.put("description", "");
                }
            }
            case CONDITIONS -> {
                for (String column : new String[]{"metadata_multijoin_uuid", "query_params", "sub_query"}) {
                    if (row.get(column) == null) {
                        row.put(column, "");
                    }
                }
                String name = row.get("metadata_name") == null ? null : row.get("metadata_name").toString();
                Metadata metadata = metadataOrNull(name);
                if (metadata == null) {
                    return "metadata '" + name + "' does not exist here";
                }
                // the metadata may have been created here with another uuid: point the condition at the local one
                if (!metadata.uuid.equals(row.get("metadata_uuid"))) {
                    Log.infof("config import: condition %s metadata_uuid %s -> %s (by name %s)", row.get(UUID),
                            row.get("metadata_uuid"), metadata.uuid, name);
                    row.put("metadata_uuid", metadata.uuid);
                }
            }
            case SELECT_QUERY -> {
                if (MetadataUtils.isReserved(row.get("query_name"))) {
                    return "query_name is reserved";
                }
                if (Boolean.TRUE.equals(row.get("api_protected")) && blank(row.get("username_field"))) {
                    return "api_protected requires username_field";
                }
            }
            case SCHEDULED_ACTIONS -> {
                if (blank(row.get("frequency"))) {
                    return "frequency is missing";
                }
                try {
                    ScheduledActionService.parse(row.get("frequency").toString());
                } catch (IllegalArgumentException e) {
                    return e.getMessage();
                }
                // never start a copied job by surprise: enabling it in the admin computes next_run
                row.put("enabled", false);
                row.put("next_run", null); // a disabled job has no next run (an overwritten row may hold one)
            }
            case PRINT_TEMPLATES -> {
                if (!PRINT_TEMPLATE_NAME.matcher(row.get("name").toString()).matches()) {
                    return "name must match " + PRINT_TEMPLATE_NAME.pattern();
                }
                String type = row.get("export_type") == null ? "" : row.get("export_type").toString().toLowerCase();
                if (!PRINT_TYPES.contains(type)) {
                    return "export_type must be one of " + PRINT_TYPES;
                }
                row.put("export_type", type);
                if (Boolean.TRUE.equals(row.get("api_protected")) && blank(row.get("username_field"))) {
                    return "api_protected requires username_field";
                }
            }
            default -> {
            }
        }
        return null;
    }

    private Map<String, Object> findExisting(String table, Map<String, Object> row) throws Exception {
        MultivaluedMap<String, String> where = new MultivaluedHashMap<>();
        for (String key : NATURAL_KEYS.get(table)) {
            where.putSingle(key, row.get(key).toString());
        }
        List<Map<String, Object>> found = jdbcRepository.list("SELECT * FROM " + table, where, null, null, 0, 0);
        if (found.size() > 1) {
            Log.warnf("config import: %s has %d rows for %s, overwriting the first", table, found.size(), where);
        }
        return found.isEmpty() ? null : found.getFirst();
    }

    private Metadata metadataOrNull(String tableName) {
        if (tableName == null) {
            return null;
        }
        try {
            return apiService.metadata(tableName);
        } catch (Exception e) {
            Log.warnf(e, "config import: metadata lookup %s failed", tableName);
            return null;
        }
    }

    /** Keeps only the known columns of {@code table}, in schema order. */
    static Map<String, Object> pick(String table, Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String column : COLUMNS.get(table)) {
            if (row.containsKey(column)) {
                out.put(column, row.get(column));
            }
        }
        return out;
    }

    private static String describe(String table, Map<String, Object> row) {
        StringBuilder sb = new StringBuilder();
        for (String key : NATURAL_KEYS.get(table)) {
            if (!sb.isEmpty()) {
                sb.append('/');
            }
            sb.append(row.get(key));
        }
        return "'" + sb + "'";
    }

    private static boolean blank(Object value) {
        return value == null || value.toString().isBlank();
    }

    private static Map<String, String> linked(String... kv) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }
}
