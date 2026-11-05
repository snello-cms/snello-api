package io.snello.service;

import io.quarkus.logging.Log;
import io.snello.api.service.JdbcRepository;
import io.snello.model.Condition;
import io.snello.model.FieldDefinition;
import io.snello.util.FieldDefinitionUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static io.snello.management.AppConstants.*;

/**
 * Keeps the physical table in sync when the user edits a metadata or one of its field definitions
 * AFTER the table has been created (see {@link MetadataService#createTableFromMetadataAndFields}).
 * <p>
 * Rules:
 * <ul>
 *     <li>columns are only ever ADDED or RENAMED, never dropped (removing a field definition keeps its column);</li>
 *     <li>column type changes are NOT applied (logged only);</li>
 *     <li>nothing happens when the table does not exist yet: the CREATE TABLE will pick the fields up;</li>
 *     <li>every hook runs BEFORE the metadata row is saved, so a failing DDL aborts the save and the user sees the error;</li>
 *     <li>every hook is idempotent, so retrying after a partial failure is safe.</li>
 * </ul>
 */
@Singleton
public class TableSchemaService {

    /** Same character set the admin allows for table/column names; anything else is refused before reaching SQL. */
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    @Inject
    JdbcRepository jdbcRepository;

    @Inject
    EventService eventService;

    // ------------------------------------------------------------------------------------------------
    // field definitions
    // ------------------------------------------------------------------------------------------------

    /**
     * A new field definition was submitted: add its column when the metadata's table already exists.
     */
    public void onFieldCreated(Map<String, Object> field) throws Exception {
        String name = str(field, "name");
        String metadataUuid = str(field, "metadata_uuid");
        Log.infof("TABLE SYNC field created: name=%s metadata_uuid=%s type=%s", name, metadataUuid, str(field, "type"));

        Map<String, Object> metadata = fetchMetadata(metadataUuid);
        if (metadata == null) {
            Log.warnf("TABLE SYNC skipped: metadata %s not found", metadataUuid);
            return;
        }
        String table = str(metadata, TABLE_NAME);
        if (!tableExists(table)) {
            Log.infof("TABLE SYNC skipped: table %s does not exist yet, CREATE TABLE will include the field", table);
            return;
        }
        addColumnIfMissing(table, metadata, new FieldDefinition(field));
    }

    /**
     * A stored field definition is being changed. {@code old} is the stored row, {@code changes} the incoming body
     * (may be partial). Renames the column when the name changed, adds it when it is missing.
     */
    public void onFieldUpdated(Map<String, Object> old, Map<String, Object> changes) throws Exception {
        Map<String, Object> merged = new HashMap<>(old);
        merged.putAll(changes);

        String oldName = str(old, "name");
        String newName = str(merged, "name");
        String metadataUuid = str(old, "metadata_uuid");
        Log.infof("TABLE SYNC field updated: uuid=%s name %s -> %s metadata_uuid=%s",
                str(old, UUID), oldName, newName, metadataUuid);

        if (!metadataUuid.equals(str(merged, "metadata_uuid"))) {
            Log.warnf("TABLE SYNC: moving a field between metadatas is not applied to tables (%s -> %s)",
                    metadataUuid, str(merged, "metadata_uuid"));
        }
        if (!str(old, "type").equals(str(merged, "type")) || !str(old, "input_type").equals(str(merged, "input_type"))) {
            Log.warnf("TABLE SYNC: type change on field %s is NOT applied to the column (only add/rename are supported)", newName);
        }

        Map<String, Object> metadata = fetchMetadata(metadataUuid);
        if (metadata == null) {
            Log.warnf("TABLE SYNC skipped: metadata %s not found", metadataUuid);
            return;
        }
        String table = str(metadata, TABLE_NAME);
        if (!tableExists(table)) {
            Log.infof("TABLE SYNC skipped: table %s does not exist yet", table);
            return;
        }

        if (oldName.equalsIgnoreCase(newName)) {
            // nothing renamed; still repair a column that is missing (e.g. schema drift)
            addColumnIfMissing(table, metadata, new FieldDefinition(merged));
            return;
        }

        requireIdentifier(newName, "column name");
        Set<String> columns = jdbcRepository.columns(table);
        Log.infof("TABLE SYNC columns of %s: %s", table, columns);
        boolean hasOld = columns.contains(oldName.toLowerCase());
        boolean hasNew = columns.contains(newName.toLowerCase());

        if (hasNew && hasOld) {
            throw new IllegalStateException("Cannot rename column '" + oldName + "' to '" + newName
                    + "' on table '" + table + "': a column named '" + newName + "' already exists");
        }
        if (hasNew) {
            Log.infof("TABLE SYNC: column %s.%s already renamed, nothing to do", table, newName);
            return;
        }
        if (!hasOld) {
            Log.warnf("TABLE SYNC: old column %s.%s not found, adding %s instead", table, oldName, newName);
            addColumnIfMissing(table, metadata, new FieldDefinition(merged));
            return;
        }
        if (oldName.equalsIgnoreCase(str(metadata, "table_key"))) {
            throw new IllegalStateException("Cannot rename '" + oldName + "': it is the table key of '" + table + "'");
        }

        String sql = jdbcRepository.renameColumnSql(table, oldName, newName);
        Log.infof("TABLE SYNC rename column: %s", sql);
        jdbcRepository.executeQuery(sql);
        Log.infof("TABLE SYNC column renamed: %s.%s -> %s", table, oldName, newName);
    }

    // ------------------------------------------------------------------------------------------------
    // metadata (table)
    // ------------------------------------------------------------------------------------------------

    /**
     * A stored metadata is being changed. Renames the table (and the references to its name) when table_name changed
     * and the table exists. Other attributes (table key, key type, ...) are never applied to the table.
     */
    public void onMetadataUpdated(Map<String, Object> old, Map<String, Object> changes) throws Exception {
        if (old == null || changes == null || changes.get(TABLE_NAME) == null) {
            return;
        }
        String oldName = str(old, TABLE_NAME);
        String newName = str(changes, TABLE_NAME);
        String uuid = str(old, UUID);
        Log.infof("TABLE SYNC metadata updated: uuid=%s table_name %s -> %s", uuid, oldName, newName);

        if (oldName.equals(newName)) {
            return;
        }
        if (!tableExists(oldName)) {
            Log.infof("TABLE SYNC skipped: table %s does not exist, nothing to rename", oldName);
            return;
        }
        requireIdentifier(newName, "table name");
        if (tableExists(newName)) {
            throw new IllegalStateException("Cannot rename table '" + oldName + "' to '" + newName
                    + "': a table with that name already exists");
        }
        refuseWhenJoinTablesAreInvolved(uuid, oldName);

        String sql = jdbcRepository.renameTableSql(oldName, newName);
        Log.infof("TABLE SYNC rename table: %s", sql);
        jdbcRepository.executeQuery(sql);

        // keep everything that points at the table by name consistent
        updateReference(FIELD_DEFINITIONS, "metadata_name", "metadata_uuid", uuid, newName);
        updateReferenceByName(FIELD_DEFINITIONS, "join_table_name", oldName, newName);
        updateReferenceByName(ACTIONS, "metadata_name", oldName, newName);
        updateReferenceByName(CONDITIONS, "metadata_name", oldName, newName);
        eventService.resetAll();
        Log.warnf("TABLE SYNC table renamed %s -> %s. Select queries, AI tools and scripts that mention the old "
                + "name in free text were NOT rewritten", oldName, newName);
    }

    // ------------------------------------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------------------------------------

    private void addColumnIfMissing(String table, Map<String, Object> metadata, FieldDefinition field) throws Exception {
        requireIdentifier(field.name, "column name");
        Set<String> columns = jdbcRepository.columns(table);
        if (columns.contains(field.name.toLowerCase())) {
            Log.infof("TABLE SYNC: column %s.%s already exists, nothing to add", table, field.name);
            return;
        }
        String columnSql = field.sql_definition != null && !field.sql_definition.trim().isEmpty()
                ? field.sql_definition
                : jdbcRepository.fieldDefinition2Sql(field);
        if (columnSql == null || columnSql.trim().isEmpty()) {
            throw new IllegalArgumentException("Unsupported field type: name='" + field.name + "', type='" + field.type + "'");
        }
        String sql = jdbcRepository.addColumnSql(table, columnSql);
        Log.infof("TABLE SYNC add column: %s", sql);
        jdbcRepository.executeQuery(sql);
        Log.infof("TABLE SYNC column added: %s.%s", table, field.name);

        if (FieldDefinitionUtils.isMultiJoinType(field.type)) {
            createJoinTable(table, str(metadata, UUID), field);
        }
    }

    /** Same join table + condition the CREATE TABLE creates for a multi-join field. */
    private void createJoinTable(String table, String metadataUuid, FieldDefinition field) throws Exception {
        if (field.join_table_name == null || field.join_table_name.isBlank()) {
            throw new IllegalArgumentException("Multi-join field '" + field.name + "' needs a join table name");
        }
        requireIdentifier(field.join_table_name, "join table name");
        String joinTableName = table + "_" + field.join_table_name;
        String tableId = table + "_id";
        String joinTableId = field.join_table_name + "_id";

        String sql = String.format(jdbcRepository.getJoinTableQuery(), joinTableName, tableId, joinTableId);
        Log.infof("TABLE SYNC create join table: %s", sql);
        jdbcRepository.executeQuery(sql);

        Condition condition = new Condition();
        condition.metadata_multijoin_uuid = metadataUuid;
        condition.uuid = java.util.UUID.randomUUID().toString();
        condition.metadata_name = field.join_table_name;
        condition.metadata_uuid = "";
        condition.condition = table + "_id_nn && join_table_nn";
        condition.query_params = table + "_id";
        condition.sub_query = field.join_table_key + " in (select " + joinTableId + " from " + joinTableName
                + " where " + tableId + " = ?)";
        jdbcRepository.create(CONDITIONS, UUID, condition.toMap());
        eventService.resetAll();
        Log.infof("TABLE SYNC join table ready: %s", joinTableName);
    }

    /**
     * Renaming a table that takes part in a multi-join (either side) would orphan the join table and its conditions,
     * which are named after both tables. Refuse until that is handled explicitly.
     */
    private void refuseWhenJoinTablesAreInvolved(String metadataUuid, String tableName) throws Exception {
        String quoted = tableName.replace("'", "''");
        List<Map<String, Object>> rows = jdbcRepository.list(
                "SELECT name, type, metadata_name FROM " + FIELD_DEFINITIONS
                        + " WHERE metadata_uuid = '" + metadataUuid.replace("'", "''") + "'"
                        + " OR join_table_name = '" + quoted + "'");
        for (Map<String, Object> row : rows) {
            if (FieldDefinitionUtils.isMultiJoinType(str(row, "type"))) {
                throw new IllegalStateException("Cannot rename table '" + tableName + "': multi-join field '"
                        + str(row, "name") + "' of '" + str(row, "metadata_name") + "' depends on its name");
            }
        }
    }

    private void updateReference(String table, String column, String whereColumn, String whereValue, String newValue) throws Exception {
        String sql = "UPDATE " + table + " SET " + column + " = ? WHERE " + whereColumn + " = ?";
        Log.infof("TABLE SYNC update references: %s [%s, %s]", sql, newValue, whereValue);
        jdbcRepository.query(sql, List.<Object>of(newValue, whereValue));
    }

    private void updateReferenceByName(String table, String column, String oldValue, String newValue) throws Exception {
        updateReference(table, column, column, oldValue, newValue);
    }

    private Map<String, Object> fetchMetadata(String metadataUuid) throws Exception {
        if (metadataUuid.isEmpty()) {
            return null;
        }
        // straight from the DB: the in-memory metadata cache is refreshed asynchronously after saves
        Map<String, Object> row = jdbcRepository.fetch(null, METADATAS, UUID, metadataUuid);
        return row == null || row.isEmpty() ? null : row;
    }

    private boolean tableExists(String table) throws Exception {
        boolean exists = !table.isEmpty() && jdbcRepository.verifyTable(table);
        Log.infof("TABLE SYNC table %s exists=%s", table, exists);
        return exists;
    }

    private static void requireIdentifier(String value, String what) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + what + " '" + value
                    + "': use letters, digits and underscore, not starting with a digit");
        }
    }

    private static String str(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? "" : value.toString();
    }
}
