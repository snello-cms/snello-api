package io.snello.service;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import io.snello.api.service.JdbcRepository;
import io.snello.management.SnelloPlane;
import io.snello.model.Metadata;
import io.snello.model.enums.SemanticType;
import io.snello.model.enums.WidgetType;
import io.snello.model.entity.EntityModel;
import io.snello.model.entity.FieldModel;
import io.snello.model.events.MetadataCreateUpdateEvent;
import io.snello.model.facet.EntityAiFacet;
import io.snello.model.facet.EntityIdentity;
import io.snello.model.facet.EntityQueryFacet;
import io.snello.model.facet.EntityUiFacet;
import io.snello.model.facet.FieldAiFacet;
import io.snello.model.facet.FieldQueryFacet;
import io.snello.model.facet.FieldUiFacet;
import io.snello.model.pojo.DbColumn;
import io.snello.model.pojo.DbForeignKey;
import io.snello.model.pojo.IntrospectRequest;
import io.snello.model.pojo.IntrospectResult;
import io.snello.model.pojo.IntrospectedEntity;
import io.snello.util.FieldUiDerivation;
import io.snello.util.MetadataUtils;
import io.snello.util.SchemaIntrospection;
import io.snello.util.SchemaIntrospection.BridgeGuess;
import io.snello.util.SchemaIntrospection.JoinGuess;
import io.snello.util.SchemaIntrospection.KeyGuess;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.snello.management.AppConstants.FIELD_DEFINITIONS;
import static io.snello.management.AppConstants.METADATAS;
import static io.snello.management.AppConstants.UUID;

/**
 * Reverse of drafts: read physical tables and propose {@code metadatas} + {@code fielddefinitions}
 * with {@code already_exist=true}. Control plane only.
 */
@ApplicationScoped
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class IntrospectService {

    @Inject
    JdbcRepository jdbcRepository;

    @Inject
    EntityCatalog catalog;

    @Inject
    MetadataService metadataService;

    @Inject
    ApiService apiService;

    @Inject
    Event<MetadataCreateUpdateEvent> metadataEvents;

    /** Dry run: derive rows, write nothing. */
    public IntrospectResult preview(IntrospectRequest request) throws Exception {
        return run(request);
    }

    /** Persist derived metadata + fields for non-skipped entities. Never creates/alters tables. */
    public IntrospectResult apply(IntrospectRequest request) throws Exception {
        IntrospectResult preview = run(request);
        if (!preview.valid()) {
            return preview;
        }
        List<IntrospectedEntity> appliedEntities = new ArrayList<>();
        List<String> warnings = new ArrayList<>(preview.warnings());
        for (IntrospectedEntity entity : preview.entities()) {
            if (entity.skipped() || entity.metadata() == null) {
                appliedEntities.add(entity);
                continue;
            }
            String metadataUuid = java.util.UUID.randomUUID().toString();
            Map<String, Object> metadataRow = new HashMap<>(entity.metadata());
            metadataRow.put(UUID, metadataUuid);
            List<Map<String, Object>> fieldRows = new ArrayList<>();
            List<String> writtenFields = new ArrayList<>();
            boolean metadataWritten = false;
            try {
                apiService.createFromMap(METADATAS, metadataRow);
                metadataWritten = true;
                for (Map<String, Object> field : entity.fields()) {
                    Map<String, Object> fieldRow = new HashMap<>(field);
                    fieldRow.put(UUID, java.util.UUID.randomUUID().toString());
                    fieldRow.put("metadata_uuid", metadataUuid);
                    fieldRow.put("metadata_name", entity.table_name());
                    apiService.createFromMap(FIELD_DEFINITIONS, fieldRow);
                    writtenFields.add((String) fieldRow.get(UUID));
                    fieldRows.add(fieldRow);
                }
                metadataEvents.fireAsync(new MetadataCreateUpdateEvent(metadataRow));
                appliedEntities.add(new IntrospectedEntity(entity.table_name(), false, null, metadataRow, fieldRows,
                        entity.warnings()));
                Log.infof("introspect apply table=%s metadata_uuid=%s fields=%d", entity.table_name(), metadataUuid,
                        fieldRows.size());
            } catch (Exception e) {
                Log.errorf(e, "introspect apply table=%s failed, rolling back", entity.table_name());
                for (String fieldUuid : writtenFields) {
                    safeDelete(FIELD_DEFINITIONS, fieldUuid);
                }
                if (metadataWritten) {
                    safeDelete(METADATAS, metadataUuid);
                }
                throw e;
            }
        }
        return new IntrospectResult(true, preview.errors(), warnings, appliedEntities, true);
    }

    private IntrospectResult run(IntrospectRequest request) throws Exception {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        IntrospectRequest req = request == null
                ? new IntrospectRequest(null, null, null)
                : request;

        Set<String> referencedTables = referencedPhysicalTables();
        List<String> physical = jdbcRepository.tables();
        Set<String> selected = resolveSelection(req, physical, referencedTables, errors, warnings);
        if (!errors.isEmpty()) {
            return new IntrospectResult(false, errors, warnings, List.of(), false);
        }

        Set<String> known = new LinkedHashSet<>(selected);
        known.addAll(referencedTables);
        Map<String, String> existingKeys = new HashMap<>();
        for (String name : catalog.queryViews().keySet()) {
            known.add(name.toLowerCase(Locale.ROOT));
            catalog.queryView(name).ifPresent(v -> {
                if (v.tableKey() != null && !v.tableKey().isBlank()) {
                    existingKeys.put(name.toLowerCase(Locale.ROOT), v.tableKey());
                }
            });
        }

        Map<String, TableInfo> infos = new LinkedHashMap<>();
        for (String table : selected) {
            infos.put(table, loadTable(table, req.sampleRowsOrDefault()));
        }

        // bridges first: skip as entities, attach multijoin later
        Map<String, BridgeGuess> bridges = new LinkedHashMap<>();
        for (Map.Entry<String, TableInfo> e : infos.entrySet()) {
            Optional<BridgeGuess> bridge = SchemaIntrospection.guessBridge(
                    e.getKey(), e.getValue().columns, e.getValue().foreignKeys, known);
            bridge.ifPresent(b -> bridges.put(e.getKey(), b));
        }

        Set<String> entityTables = new LinkedHashSet<>();
        for (String table : selected) {
            if (!bridges.containsKey(table)) {
                entityTables.add(table);
            }
        }
        known.addAll(entityTables);

        // provisional key map for join_table_key resolution inside the batch
        Map<String, KeyGuess> keys = new HashMap<>();
        for (String table : entityTables) {
            TableInfo info = infos.get(table);
            keys.put(table, SchemaIntrospection.guessKey(info.primaryKeys, info.columns));
        }
        for (Map.Entry<String, String> e : existingKeys.entrySet()) {
            if (!keys.containsKey(e.getKey())) {
                keys.put(e.getKey(), new KeyGuess(e.getValue(), "uuid", false));
            }
        }

        Map<String, List<FieldModel>> multijoinsByOwner = new HashMap<>();
        for (Map.Entry<String, BridgeGuess> e : bridges.entrySet()) {
            BridgeGuess b = e.getValue();
            String owner = entityTables.contains(b.leftTable()) ? b.leftTable()
                    : entityTables.contains(b.rightTable()) ? b.rightTable() : null;
            String other = owner == null ? null
                    : owner.equals(b.leftTable()) ? b.rightTable() : b.leftTable();
            if (owner == null || other == null) {
                warnings.add("bridge table '" + e.getKey() + "' skipped: ends " + b.leftTable() + "/" + b.rightTable()
                        + " not both available as entities");
                continue;
            }
            KeyGuess otherKey = keys.getOrDefault(other, new KeyGuess("uuid", "uuid", false));
            FieldModel mj = buildMultijoinField(owner, other, otherKey.column());
            multijoinsByOwner.computeIfAbsent(owner, k -> new ArrayList<>()).add(mj);
            warnings.add("bridge '" + e.getKey() + "' → multijoin on '" + owner + "' → '" + other + "'");
        }

        List<IntrospectedEntity> entities = new ArrayList<>();
        for (String table : selected) {
            if (bridges.containsKey(table)) {
                entities.add(IntrospectedEntity.skipped(table, "bridge table (mapped as multijoin when possible)"));
                continue;
            }
            if (MetadataUtils.isReserved(table)) {
                entities.add(IntrospectedEntity.skipped(table, "reserved system table"));
                continue;
            }

            List<String> tableWarnings = new ArrayList<>();
            IntrospectedEntity built = buildEntity(table, infos.get(table), keys.get(table), known, keys,
                    multijoinsByOwner.getOrDefault(table, List.of()), req.metadata_group(), tableWarnings);
            entities.add(built);
            warnings.addAll(tableWarnings);
        }

        boolean valid = errors.isEmpty();
        Log.infof("introspect preview tables=%d entities=%d valid=%s", selected.size(), entities.size(), valid);
        return new IntrospectResult(valid, errors, warnings, entities, false);
    }

    /**
     * Physical table names already owned by metadata: {@code table_name} and non-blank {@code alias_table}.
     */
    private Set<String> referencedPhysicalTables() throws Exception {
        Set<String> referenced = new HashSet<>();
        for (Metadata metadata : metadataService.metadataMap().values()) {
            if (metadata == null) {
                continue;
            }
            if (metadata.table_name != null && !metadata.table_name.isBlank()) {
                referenced.add(metadata.table_name.toLowerCase(Locale.ROOT));
            }
            if (metadata.alias_table != null && !metadata.alias_table.isBlank()) {
                referenced.add(metadata.alias_table.toLowerCase(Locale.ROOT));
            }
        }
        return referenced;
    }

    private Set<String> resolveSelection(IntrospectRequest req, List<String> physical,
                                         Set<String> referencedTables, List<String> errors, List<String> warnings) {
        Set<String> physicalSet = new HashSet<>(physical);
        Set<String> selected = new LinkedHashSet<>();
        int skippedExisting = 0;
        if (req.tables() == null || req.tables().isEmpty()) {
            for (String table : physical) {
                if (MetadataUtils.isReserved(table)) {
                    continue;
                }
                if (referencedTables.contains(table)) {
                    skippedExisting++;
                    continue;
                }
                selected.add(table);
            }
            if (skippedExisting > 0) {
                warnings.add("skipped " + skippedExisting + " table(s) already referenced by metadata");
            }
            if (selected.isEmpty()) {
                warnings.add("no user tables found to introspect");
            }
            return selected;
        }
        for (String raw : req.tables()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String table = raw.trim().toLowerCase(Locale.ROOT);
            if (MetadataUtils.isReserved(table)) {
                warnings.add("skipping reserved table '" + table + "'");
                continue;
            }
            if (!physicalSet.contains(table)) {
                errors.add("table '" + table + "' not found in database");
                continue;
            }
            if (referencedTables.contains(table)) {
                warnings.add("skipping '" + table + "': already referenced by metadata");
                skippedExisting++;
                continue;
            }
            selected.add(table);
        }
        return selected;
    }

    private TableInfo loadTable(String table, int sampleLimit) throws Exception {
        List<DbColumn> columns = jdbcRepository.columnDetails(table);
        List<String> pks = jdbcRepository.primaryKeys(table);
        List<DbForeignKey> fks = jdbcRepository.importedKeys(table);
        List<Map<String, Object>> samples = jdbcRepository.sampleRows(table, sampleLimit);
        return new TableInfo(columns, pks, fks, samples);
    }

    private IntrospectedEntity buildEntity(String table, TableInfo info, KeyGuess key, Set<String> known,
                                           Map<String, KeyGuess> keys, List<FieldModel> extraMultijoins,
                                           String metadataGroup, List<String> warnings) {
        EntityModel entity = new EntityModel();
        EntityIdentity.Builder identity = EntityIdentity.builder(entity.identity);
        identity.table_name = table;
        identity.metadata_group = metadataGroup;
        identity.already_exist = true;
        identity.created = false;
        entity.identity = identity.build();

        EntityQueryFacet.Builder query = EntityQueryFacet.builder(entity.query);
        query.table_key = key.column();
        query.table_key_type = key.keyType();
        entity.query = query.build();
        entity.ai = EntityAiFacet.ofDescription("Introspected from table " + table);
        entity.ui = EntityUiFacet.empty();

        List<FieldModel> fields = new ArrayList<>();
        int viewIndex = 1;
        Set<String> keyColumns = new HashSet<>();
        if (!key.declareKeyField()) {
            keyColumns.add(key.column());
        }

        for (DbColumn column : info.columns) {
            String name = column.name();
            if (keyColumns.contains(name)) {
                continue;
            }
            List<Object> samples = columnSamples(info.samples, name);
            Optional<JoinGuess> join = SchemaIntrospection.guessJoin(name, info.foreignKeys, known);
            FieldModel field;
            if (join.isPresent()) {
                JoinGuess j = join.get();
                String targetKey = keys.containsKey(j.targetTable())
                        ? keys.get(j.targetTable()).column()
                        : j.targetKey();
                field = buildJoinField(table, column, j.targetTable(), targetKey, viewIndex);
                if (!j.fromFk()) {
                    warnings.add(table + "." + name + ": join inferred from column name → " + j.targetTable());
                }
            } else {
                SemanticType semantic = SchemaIntrospection.semanticFor(column, samples);
                field = buildScalarField(table, column, semantic, key, viewIndex);
                if (name.endsWith("_id") || name.endsWith("_uuid")) {
                    warnings.add(table + "." + name + ": kept as plain key (no obvious join target)");
                }
            }
            fields.add(field);
            viewIndex++;
        }
        for (FieldModel mj : extraMultijoins) {
            FieldUiFacet.Builder ui = FieldUiFacet.builder(mj.ui);
            ui.view_index = viewIndex++;
            mj.ui = ui.build();
            fields.add(mj);
        }

        List<String> searchable = new ArrayList<>();
        for (FieldModel f : fields) {
            if (f.query.searchable()) {
                searchable.add(f.name);
            }
        }
        if (!searchable.isEmpty()) {
            EntityUiFacet.Builder ui = EntityUiFacet.builder(entity.ui);
            ui.search_fields = String.join(",", searchable);
            entity.ui = ui.build();
        }

        Map<String, Object> metadataRow = entity.toMap();
        List<Map<String, Object>> fieldRows = fields.stream().map(FieldModel::toMap).toList();
        return new IntrospectedEntity(table, false, null, metadataRow, fieldRows, List.copyOf(warnings));
    }

    private static FieldModel buildScalarField(String table, DbColumn column, SemanticType semantic, KeyGuess key,
                                               int viewIndex) {
        FieldModel model = new FieldModel();
        model.metadata_name = table;
        model.name = column.name();
        String[] uiType = FieldUiDerivation.uiTypeFor(semantic.dbValue());

        FieldQueryFacet.Builder query = FieldQueryFacet.builder(model.query);
        query.sql_type = column.sqlType();
        boolean isKey = key.declareKeyField() && key.column().equals(column.name());
        query.table_key = isKey;
        boolean textLike = FieldUiDerivation.textLike(semantic.dbValue());
        query.searchable = textLike || semantic == SemanticType.INTEGER || semantic == SemanticType.DECIMAL
                || semantic == SemanticType.BOOLEAN || semantic == SemanticType.DATE || semantic == SemanticType.DATETIME;
        if (query.searchable) {
            if (textLike) {
                query.search_condition = "contains";
                query.search_field_name = column.name() + "_contains";
            } else {
                query.search_condition = "=";
                query.search_field_name = column.name();
            }
        }
        model.query = query.build();

        FieldUiFacet.Builder ui = FieldUiFacet.builder(model.ui);
        ui.label = FieldUiDerivation.label(column.name());
        ui.type = uiType[0];
        ui.input_type = uiType[1];
        ui.view_index = viewIndex;
        ui.show_in_list = FieldUiDerivation.shownInListByDefault(semantic.dbValue());
        ui.mandatory = !column.nullable() && !isKey;
        model.ui = ui.build();

        FieldAiFacet.Builder ai = FieldAiFacet.builder(model.ai);
        ai.semantic_type = semantic.dbValue();
        ai.description = "Column " + column.name();
        model.ai = ai.build();
        return model;
    }

    private static FieldModel buildJoinField(String table, DbColumn column, String target, String targetKey,
                                             int viewIndex) {
        FieldModel model = new FieldModel();
        model.metadata_name = table;
        model.name = column.name();

        FieldQueryFacet.Builder query = FieldQueryFacet.builder(model.query);
        query.sql_type = column.sqlType();
        query.join_table_name = target;
        query.join_table_key = targetKey;
        query.searchable = true;
        query.search_condition = "=";
        query.search_field_name = column.name();
        model.query = query.build();

        FieldUiFacet.Builder ui = FieldUiFacet.builder(model.ui);
        ui.label = FieldUiDerivation.label(column.name());
        ui.type = WidgetType.JOIN.dbValue();
        ui.view_index = viewIndex;
        ui.show_in_list = true;
        ui.mandatory = !column.nullable();
        model.ui = ui.build();

        FieldAiFacet.Builder ai = FieldAiFacet.builder(model.ai);
        ai.semantic_type = SemanticType.REFERENCE.dbValue();
        ai.description = "Reference to " + target;
        model.ai = ai.build();
        return model;
    }

    private static FieldModel buildMultijoinField(String owner, String other, String otherKey) {
        FieldModel model = new FieldModel();
        model.metadata_name = owner;
        model.name = other;

        FieldQueryFacet.Builder query = FieldQueryFacet.builder(model.query);
        query.join_table_name = other;
        query.join_table_key = otherKey;
        query.searchable = false;
        model.query = query.build();

        FieldUiFacet.Builder ui = FieldUiFacet.builder(model.ui);
        ui.label = FieldUiDerivation.label(other);
        ui.type = WidgetType.MULTIJOIN.dbValue();
        ui.show_in_list = false;
        model.ui = ui.build();

        FieldAiFacet.Builder ai = FieldAiFacet.builder(model.ai);
        ai.semantic_type = SemanticType.REFERENCES.dbValue();
        ai.description = "Many-to-many with " + other;
        model.ai = ai.build();
        return model;
    }

    private static List<Object> columnSamples(List<Map<String, Object>> rows, String column) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Object> values = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (row.containsKey(column)) {
                values.add(row.get(column));
            }
        }
        return values;
    }

    private void safeDelete(String table, String uuid) {
        try {
            jdbcRepository.delete(table, UUID, uuid);
        } catch (Exception e) {
            Log.warnf(e, "rollback delete failed table=%s uuid=%s", table, uuid);
        }
    }

    private record TableInfo(
            List<DbColumn> columns,
            List<String> primaryKeys,
            List<DbForeignKey> foreignKeys,
            List<Map<String, Object>> samples) {
    }
}
