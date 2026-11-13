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

import static io.snello.management.AppConstants.DOCUMENTS;
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

    /** More distinct values than this is free text, not a select. */
    private static final int ENUM_MAX_VALUES = 30;
    /** Sample values checked against a join target. */
    private static final int JOIN_CHECK_VALUES = 30;
    private static final int MIN_LIST_FIELDS = 3;

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
        List<Map<String, Object>> writtenMetadata = new ArrayList<>();
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
                writtenMetadata.add(metadataRow);
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
        // Invalidate here, synchronously: a caller reading metadata right away (admin opening the new table, the
        // next request) must not see the old map. Events go out only now that every row is written: their
        // observer rebuilds the caches on another thread, and a rebuild started mid-write would store a snapshot
        // missing the later tables.
        metadataService.invalidateMetadataCaches();
        for (Map<String, Object> metadataRow : writtenMetadata) {
            metadataEvents.fireAsync(new MetadataCreateUpdateEvent(metadataRow));
        }
        Log.infof("introspect apply done entities=%d, metadata caches invalidated", writtenMetadata.size());
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

        // a snello-style bridge (a_b with a_id + b_id) is still generated as a plain entity with two joins:
        // a multijoin field keeps its values in a column of the owner table, which an existing bridge does not have
        for (Map.Entry<String, TableInfo> e : infos.entrySet()) {
            SchemaIntrospection.guessBridge(e.getKey(), e.getValue().columns, e.getValue().foreignKeys, known)
                    .ifPresent(b -> {
                        Log.infof("introspect table=%s is a bridge %s/%s: generated as entity with two joins",
                                e.getKey(), b.leftTable(), b.rightTable());
                        warnings.add("bridge '" + e.getKey() + "' (" + b.leftTable() + " ↔ " + b.rightTable()
                                + ") generated as a table with two joins, not as a multijoin field: a multijoin "
                                + "stores its values in a column of '" + b.leftTable() + "'");
                    });
        }

        // provisional key map for join_table_key resolution inside the batch
        Map<String, KeyGuess> keys = new HashMap<>();
        Map<String, List<String>> keyWarnings = new HashMap<>();
        for (String table : selected) {
            List<String> tableWarnings = new ArrayList<>();
            keys.put(table, guessKey(table, infos.get(table), tableWarnings));
            keyWarnings.put(table, tableWarnings);
        }
        for (Map.Entry<String, String> e : existingKeys.entrySet()) {
            if (!keys.containsKey(e.getKey())) {
                keys.put(e.getKey(), new KeyGuess(e.getValue(), "uuid", false));
            }
        }

        List<IntrospectedEntity> entities = new ArrayList<>();
        for (String table : selected) {
            if (MetadataUtils.isReserved(table)) {
                entities.add(IntrospectedEntity.skipped(table, "reserved system table"));
                continue;
            }

            List<String> tableWarnings = new ArrayList<>(keyWarnings.get(table));
            IntrospectedEntity built = buildEntity(table, infos.get(table), keys.get(table), known, keys,
                    req.metadata_group(), tableWarnings);
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

    /**
     * Key from the JDBC primary key, then refined with the data: a varchar key whose values are slugs of another
     * column is a {@code slug} key built from that column.
     */
    private KeyGuess guessKey(String table, TableInfo info, List<String> warnings) {
        KeyGuess key = SchemaIntrospection.guessKey(info.primaryKeys, info.columns);
        if (info.primaryKeys.size() > 1) {
            warnings.add(table + ": composite primary key " + info.primaryKeys + ", only '" + key.column()
                    + "' is used as key: rows sharing it cannot be told apart");
        } else if (info.primaryKeys.isEmpty()) {
            warnings.add(table + ": no primary key, '" + key.column() + "' is used as key (guessed)");
        }
        if ("uuid".equals(key.keyType())) {
            List<Object> keyValues = columnSamples(info.samples, key.column());
            if (!distinctStrings(keyValues, 1).isEmpty() && !SchemaIntrospection.allUuidValues(keyValues)) {
                Optional<String> slugSource = SchemaIntrospection.guessSlugSource(info.samples, key.column(),
                        info.columns);
                if (slugSource.isPresent()) {
                    Log.infof("introspect table=%s key=%s values are slugs of %s", table, key.column(),
                            slugSource.get());
                    return new KeyGuess(key.column(), "slug", false, slugSource.get());
                }
                Log.infof("introspect table=%s key=%s values are neither uuids nor slugs of a column: kept as uuid",
                        table, key.column());
            }
        }
        Log.infof("introspect table=%s key=%s key_type=%s pk=%s", table, key.column(), key.keyType(),
                info.primaryKeys);
        return key;
    }

    private IntrospectedEntity buildEntity(String table, TableInfo info, KeyGuess key, Set<String> known,
                                           Map<String, KeyGuess> keys, String metadataGroup, List<String> warnings) {
        EntityModel entity = new EntityModel();
        EntityIdentity.Builder identity = EntityIdentity.builder(entity.identity);
        identity.table_name = table;
        identity.metadata_group = metadataGroup;
        identity.already_exist = true;
        identity.created = false;
        entity.identity = identity.build();

        String orderBy = SchemaIntrospection.guessOrderBy(info.columns, key.column());
        String orderColumn = orderBy == null ? null : orderBy.substring(0, orderBy.indexOf(' '));
        EntityQueryFacet.Builder query = EntityQueryFacet.builder(entity.query);
        query.table_key = key.column();
        query.table_key_type = key.keyType();
        query.table_key_addition = key.addition();
        query.order_by = orderBy;
        entity.query = query.build();
        entity.ai = EntityAiFacet.ofDescription("Introspected from table " + table);
        entity.ui = EntityUiFacet.empty();
        Log.infof("introspect table=%s order_by=%s key_addition=%s columns=%d samples=%d", table, orderBy,
                key.addition(), info.columns.size(), info.samples.size());

        List<FieldModel> fields = new ArrayList<>();
        int viewIndex = 1;
        for (DbColumn column : info.columns) {
            String name = column.name();
            boolean isKey = key.column().equals(name);
            if (isKey && !key.declareKeyField()) {
                continue;
            }
            List<Object> samples = columnSamples(info.samples, name);
            // a guessed or shared key can itself be a reference (bridge rows, one-to-one tables)
            FieldModel field = referenceField(table, column, samples, info, known, keys, viewIndex, warnings);
            if (field != null && isKey) {
                FieldQueryFacet.Builder keyed = FieldQueryFacet.builder(field.query);
                keyed.table_key = true;
                field.query = keyed.build();
            }
            if (field == null) {
                SemanticType semantic = SchemaIntrospection.semanticFor(column, samples);
                if (!isKey && isPlainText(semantic) && isDocumentReference(samples)) {
                    Log.infof("introspect %s.%s values are document uuids: media", table, name);
                    semantic = SemanticType.MEDIA;
                }
                field = buildScalarField(table, column, semantic, key, viewIndex, name.equals(orderColumn));
                if (name.endsWith("_id") || name.endsWith("_uuid")) {
                    warnings.add(table + "." + name + ": kept as plain key (no obvious join target)");
                }
            }
            fields.add(field);
            viewIndex++;
        }
        fillSparseList(table, fields);

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

    /**
     * Join or select for a column that references something, null for a plain scalar. In order of confidence:
     * JDBC FK; a name pointing at a known table, confirmed by the values; a small closed set of values that are all
     * keys of one table (join) or just a closed set (select with those options).
     */
    private FieldModel referenceField(String table, DbColumn column, List<Object> samples, TableInfo info,
                                      Set<String> known, Map<String, KeyGuess> keys, int viewIndex,
                                      List<String> warnings) {
        String name = column.name();
        Optional<JoinGuess> named = SchemaIntrospection.guessJoin(table, name, info.foreignKeys, known);
        if (named.isPresent()) {
            JoinGuess j = named.get();
            String targetKey = keys.containsKey(j.targetTable()) ? keys.get(j.targetTable()).column() : j.targetKey();
            String confirmedKey = j.fromFk() ? targetKey : confirmJoinKey(table, name, j.targetTable(), targetKey,
                    samples, warnings);
            if (confirmedKey != null) {
                return buildJoinField(table, column, j.targetTable(), confirmedKey,
                        joinLabelColumn(j.targetTable(), confirmedKey), viewIndex);
            }
        }

        if (!isPlainText(SchemaIntrospection.semanticFor(column, samples)) || !mayBeEnum(samples)) {
            return null;
        }
        Map<String, Long> counts = jdbcRepository.valueCounts(table, name, ENUM_MAX_VALUES);
        if (!SchemaIntrospection.looksLikeEnum(counts, ENUM_MAX_VALUES)) {
            Log.debugf("introspect %s.%s not an enumeration (distinct=%s)", table, name,
                    counts == null ? "many" : counts.size());
            return null;
        }
        Optional<JoinGuess> byValues = joinByValues(table, counts.keySet(), keys);
        if (byValues.isPresent()) {
            JoinGuess j = byValues.get();
            Log.infof("introspect %s.%s every value is a key of %s.%s: join", table, name, j.targetTable(),
                    j.targetKey());
            warnings.add(table + "." + name + ": join inferred from values → " + j.targetTable());
            return buildJoinField(table, column, j.targetTable(), j.targetKey(),
                    joinLabelColumn(j.targetTable(), j.targetKey()), viewIndex);
        }
        Log.infof("introspect %s.%s closed set of %d values: select", table, name, counts.size());
        warnings.add(table + "." + name + ": select with the " + counts.size() + " values found in the data");
        return buildSelectField(table, column, new java.util.TreeSet<>(counts.keySet()), viewIndex);
    }

    /**
     * Key column of {@code target} the values of a name-matched column really point at, or null when the data says
     * the name is a coincidence. With no data the name is all there is, and it is accepted.
     */
    private String confirmJoinKey(String table, String column, String target, String targetKey, List<Object> samples,
                                  List<String> warnings) {
        List<String> values = distinctStrings(samples, JOIN_CHECK_VALUES);
        if (values.isEmpty()) {
            warnings.add(table + "." + column + ": join inferred from column name → " + target
                    + " (no data to confirm)");
            return targetKey;
        }
        List<String> candidates = "id".equals(targetKey) ? List.of(targetKey) : List.of(targetKey, "id");
        for (String candidate : candidates) {
            int found = jdbcRepository.countMatching(target, candidate, values);
            Log.infof("introspect %s.%s → %s.%s: %d of %d sample values exist", table, column, target, candidate,
                    found, values.size());
            // dangling references exist in real data: most values matching is enough
            if (found > 0 && found * 100 / values.size() >= 80) {
                warnings.add(table + "." + column + ": join inferred from column name → " + target);
                return candidate;
            }
        }
        return null;
    }

    /** The one entity table whose keys contain every value, if any. Values with spaces are never keys. */
    private Optional<JoinGuess> joinByValues(String table, Set<String> values, Map<String, KeyGuess> keys) {
        for (String value : values) {
            if (value.chars().anyMatch(Character::isWhitespace)) {
                return Optional.empty();
            }
        }
        for (Map.Entry<String, KeyGuess> e : keys.entrySet()) {
            if (e.getKey().equals(table)) {
                continue;
            }
            if (jdbcRepository.countMatching(e.getKey(), e.getValue().column(), values) == values.size()) {
                return Optional.of(new JoinGuess(e.getKey(), e.getValue().column(), false));
            }
        }
        return Optional.empty();
    }

    /** Cheap pre-check on the sample before counting values on the whole table. */
    private static boolean mayBeEnum(List<Object> samples) {
        List<String> all = new ArrayList<>();
        for (Object v : samples) {
            if (v != null && !String.valueOf(v).isBlank()) {
                all.add(String.valueOf(v));
            }
        }
        if (all.size() < SchemaIntrospection.ENUM_MIN_ROWS) {
            return false;
        }
        Set<String> distinct = new HashSet<>(all);
        return distinct.size() * 10 <= all.size() * 6
                && distinct.stream().allMatch(v -> v.length() <= SchemaIntrospection.ENUM_VALUE_MAX_LENGTH);
    }

    private static boolean isPlainText(SemanticType semantic) {
        return semantic == SemanticType.TEXT || semantic == SemanticType.LONG_TEXT;
    }

    /** Uploaded files are referenced by their {@code documents.uuid}. */
    private boolean isDocumentReference(List<Object> samples) {
        if (!SchemaIntrospection.allUuidValues(samples)) {
            return false;
        }
        List<String> values = distinctStrings(samples, 10);
        int found = jdbcRepository.countMatching(DOCUMENTS, UUID, values);
        return found > 0 && found * 100 / values.size() >= 80;
    }

    /** A list showing one or two columns is useless: add the first short columns until it has three. */
    private static void fillSparseList(String table, List<FieldModel> fields) {
        long listed = fields.stream().filter(f -> f.ui.show_in_list()).count();
        for (FieldModel f : fields) {
            if (listed >= MIN_LIST_FIELDS) {
                break;
            }
            SemanticType semantic = SemanticType.from(f.ai.semantic_type());
            boolean shortValue = semantic == SemanticType.TEXT || semantic == SemanticType.INTEGER
                    || semantic == SemanticType.DECIMAL || semantic == SemanticType.DATE
                    || semantic == SemanticType.DATETIME || semantic == SemanticType.EMAIL
                    || semantic == SemanticType.URL;
            if (f.ui.show_in_list() || !shortValue || SchemaIntrospection.isAuditColumn(f.name)) {
                continue;
            }
            FieldUiFacet.Builder ui = FieldUiFacet.builder(f.ui);
            ui.show_in_list = true;
            f.ui = ui.build();
            listed++;
            Log.debugf("introspect %s.%s added to a sparse list", table, f.name);
        }
    }

    /**
     * Search form and list columns are suggestions the schema cannot state. Hand-made metadata keeps both short:
     * the columns a person recognises a row by (name, title, code...), flags and tags; dates only when the list is
     * sorted by them. References and selects are handled by their own builders.
     */
    private static FieldModel buildScalarField(String table, DbColumn column, SemanticType semantic, KeyGuess key,
                                               int viewIndex, boolean orderColumn) {
        FieldModel model = new FieldModel();
        model.metadata_name = table;
        model.name = column.name();
        String[] uiType = FieldUiDerivation.uiTypeFor(semantic.dbValue());

        FieldQueryFacet.Builder query = FieldQueryFacet.builder(model.query);
        query.sql_type = column.sqlType();
        boolean isKey = key.declareKeyField() && key.column().equals(column.name());
        query.table_key = isKey;
        boolean textLike = FieldUiDerivation.textLike(semantic.dbValue());
        // rich-text bodies stay out of the search form (it would render a full editor)
        boolean richText = WidgetType.TINYMCE.dbValue().equals(uiType[0]);
        boolean recognisable = isKey || semantic == SemanticType.BOOLEAN || semantic == SemanticType.TAGS
                || SchemaIntrospection.isLabelLike(column.name()) && textLike && !richText;
        query.searchable = recognisable;
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
        // a title declared as text is still what a row is recognised by
        boolean listable = FieldUiDerivation.shownInListByDefault(semantic.dbValue())
                || semantic == SemanticType.LONG_TEXT && SchemaIntrospection.isLabelLike(column.name());
        ui.show_in_list = listable && (recognisable || orderColumn) || isKey;
        ui.mandatory = requiredByDatabase(column) && !isKey;
        model.ui = ui.build();

        FieldAiFacet.Builder ai = FieldAiFacet.builder(model.ai);
        ai.semantic_type = semantic.dbValue();
        ai.description = "Column " + column.name();
        model.ai = ai.build();
        return model;
    }

    /** NOT NULL with no default: an insert that omits the column fails, so the form must ask for it. */
    private static boolean requiredByDatabase(DbColumn column) {
        return !column.nullable() && !column.autoIncrement()
                && (column.columnDef() == null || column.columnDef().isBlank());
    }

    /**
     * Display column of a join target, read from the live table (target may be outside this batch).
     * Without it the admin join widget has nothing to show and crashes on a null select_fields.
     */
    private String joinLabelColumn(String target, String targetKey) {
        try {
            String label = SchemaIntrospection.guessLabelColumn(jdbcRepository.columnDetails(target), targetKey);
            Log.debugf("introspect join target=%s key=%s label=%s", target, targetKey, label);
            return label;
        } catch (Exception e) {
            Log.warnf("introspect join target=%s: cannot read columns (%s), label falls back to key %s",
                    target, e.getMessage(), targetKey);
            return targetKey;
        }
    }

    private static FieldModel buildJoinField(String table, DbColumn column, String target, String targetKey,
                                             String labelColumn, int viewIndex) {
        FieldModel model = new FieldModel();
        model.metadata_name = table;
        model.name = column.name();

        FieldQueryFacet.Builder query = FieldQueryFacet.builder(model.query);
        query.sql_type = column.sqlType();
        query.join_table_name = target;
        query.join_table_key = targetKey;
        query.join_table_select_fields = labelColumn;
        query.searchable = true;
        query.search_condition = "=";
        query.search_field_name = column.name();
        model.query = query.build();

        FieldUiFacet.Builder ui = FieldUiFacet.builder(model.ui);
        ui.label = FieldUiDerivation.label(column.name());
        ui.type = WidgetType.JOIN.dbValue();
        ui.view_index = viewIndex;
        ui.show_in_list = true;
        ui.mandatory = requiredByDatabase(column);
        model.ui = ui.build();

        FieldAiFacet.Builder ai = FieldAiFacet.builder(model.ai);
        ai.semantic_type = SemanticType.REFERENCE.dbValue();
        ai.description = "Reference to " + target;
        model.ai = ai.build();
        return model;
    }

    /** Options are the values found in the data: a starting point, the full domain may be larger. */
    private static FieldModel buildSelectField(String table, DbColumn column, java.util.Collection<String> options,
                                               int viewIndex) {
        FieldModel model = new FieldModel();
        model.metadata_name = table;
        model.name = column.name();

        FieldQueryFacet.Builder query = FieldQueryFacet.builder(model.query);
        query.sql_type = column.sqlType();
        query.searchable = true;
        query.search_condition = "=";
        query.search_field_name = column.name();
        model.query = query.build();

        FieldUiFacet.Builder ui = FieldUiFacet.builder(model.ui);
        ui.label = FieldUiDerivation.label(column.name());
        ui.type = WidgetType.SELECT.dbValue();
        ui.options = String.join(",", options);
        ui.view_index = viewIndex;
        ui.show_in_list = true;
        ui.mandatory = requiredByDatabase(column);
        model.ui = ui.build();

        FieldAiFacet.Builder ai = FieldAiFacet.builder(model.ai);
        ai.semantic_type = SemanticType.ENUM.dbValue();
        ai.description = "One of: " + String.join(", ", options);
        model.ai = ai.build();
        return model;
    }

    /** Distinct non-blank values as strings, at most {@code limit}. */
    private static List<String> distinctStrings(List<Object> values, int limit) {
        Set<String> distinct = new LinkedHashSet<>();
        for (Object v : values) {
            if (v != null && !String.valueOf(v).isBlank()) {
                distinct.add(String.valueOf(v));
                if (distinct.size() >= limit) {
                    break;
                }
            }
        }
        return new ArrayList<>(distinct);
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
