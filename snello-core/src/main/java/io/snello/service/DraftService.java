package io.snello.service;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import io.snello.api.service.JdbcRepository;
import io.snello.management.SnelloPlane;
import io.snello.model.Condition;
import io.snello.model.FieldDefinition;
import io.snello.model.Metadata;
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
import io.snello.model.enums.WidgetType;
import io.snello.model.pojo.ColumnSpec;
import io.snello.model.pojo.DraftResult;
import io.snello.model.pojo.EntityDraft;
import io.snello.model.pojo.EntityQueryView;
import io.snello.model.pojo.FieldDraft;
import io.snello.util.ColumnSpecs;
import io.snello.util.FieldUiDerivation;
import io.snello.util.MetadataUtils;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;
import java.util.regex.Pattern;

import static io.snello.management.AppConstants.*;

/**
 * Turns AI-authored {@link EntityDraft}s into metadata (roadmap step 2): validate → derive the UI facet →
 * dry-run the DDL → (apply) persist rows and optionally create the table.
 * <p>
 * Exposed by {@code DraftServiceRs} ({@code /api/drafts}). Control plane only.
 */
@Singleton
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class DraftService {

    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]{0,62}");
    private static final Set<String> KEY_TYPES = Set.of("uuid", "slug", "autoincrement", "userdefined");

    @Inject
    MetadataService metadataService;

    @Inject
    EntityCatalog catalog;

    @Inject
    JdbcRepository jdbcRepository;

    @Inject
    ApiService apiService;

    @Inject
    Event<MetadataCreateUpdateEvent> metadataEvents;

    /** Dry run: validation, derivations and DDL preview; writes nothing. */
    public DraftResult validate(EntityDraft draft) throws Exception {
        String tableName = draft == null ? null : draft.table_name();
        Log.infof("draft validate table=%s fields=%d", tableName,
                draft == null || draft.fields() == null ? 0 : draft.fields().size());
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (draft == null) {
            errors.add("draft is empty");
            return new DraftResult(false, errors, warnings, null, List.of(), List.of(), false, false);
        }

        EntityModel entity = buildEntity(draft, errors, warnings);
        List<FieldModel> fields = buildFields(draft, entity, errors, warnings);
        if (entity.ui.search_fields() == null) {
            EntityUiFacet.Builder ui = EntityUiFacet.builder(entity.ui);
            ui.search_fields = deriveSearchFields(fields);
            entity.ui = ui.build();
        }

        List<String> ddl = List.of();
        if (errors.isEmpty()) {
            ddl = previewDdl(entity, fields, errors);
        }
        Map<String, Object> metadataRow = entity.toMap();
        List<Map<String, Object>> fieldRows = fields.stream().map(FieldModel::toMap).toList();
        boolean valid = errors.isEmpty();
        Log.infof("draft validate table=%s valid=%s errors=%s warnings=%s", tableName, valid, errors, warnings);
        return new DraftResult(valid, errors, warnings, metadataRow, fieldRows, ddl, false, false);
    }

    /**
     * Validates, then persists the metadata + field rows; with {@code createTable} also runs the DDL. On a failure
     * part-way the rows written so far are removed (best effort: the JDBC layer has no shared transaction).
     */
    public DraftResult apply(EntityDraft draft, boolean createTable) throws Exception {
        DraftResult result = validate(draft);
        if (!result.valid()) {
            return result;
        }
        String metadataUuid = java.util.UUID.randomUUID().toString();
        Map<String, Object> metadataRow = new HashMap<>(result.metadata());
        metadataRow.put(UUID, metadataUuid);
        List<Map<String, Object>> fieldRows = new ArrayList<>();
        for (Map<String, Object> row : result.fields()) {
            Map<String, Object> fieldRow = new HashMap<>(row);
            fieldRow.put(UUID, java.util.UUID.randomUUID().toString());
            fieldRow.put("metadata_uuid", metadataUuid);
            fieldRow.put("metadata_name", draft.table_name());
            fieldRows.add(fieldRow);
        }
        Log.infof("draft apply table=%s metadata_uuid=%s fields=%d createTable=%s",
                draft.table_name(), metadataUuid, fieldRows.size(), createTable);

        List<String> writtenFields = new ArrayList<>();
        boolean metadataWritten = false;
        boolean tableCreated = false;
        try {
            apiService.createFromMap(METADATAS, metadataRow);
            metadataWritten = true;
            for (Map<String, Object> fieldRow : fieldRows) {
                apiService.createFromMap(FIELD_DEFINITIONS, fieldRow);
                writtenFields.add((String) fieldRow.get(UUID));
            }
            if (createTable) {
                Metadata metadata = new Metadata(metadataRow);
                List<FieldDefinition> definitions = fieldRows.stream().map(FieldDefinition::new).toList();
                metadataService.createTableFromMetadataAndFields(metadata, definitions);
                apiService.merge(METADATAS, new HashMap<>(Map.of(CREATED, true)), metadataUuid, UUID);
                metadataRow.put(CREATED, true);
                tableCreated = true;
                Log.infof("draft apply table=%s: table created", draft.table_name());
            }
        } catch (Exception e) {
            Log.errorf(e, "draft apply table=%s failed, rolling back %d field rows and metadata=%s",
                    draft.table_name(), writtenFields.size(), metadataWritten);
            for (String fieldUuid : writtenFields) {
                safeDelete(FIELD_DEFINITIONS, fieldUuid);
            }
            if (metadataWritten) {
                safeDelete(METADATAS, metadataUuid);
            }
            throw e;
        } finally {
            metadataEvents.fireAsync(new MetadataCreateUpdateEvent(metadataRow));
        }
        return new DraftResult(true, result.errors(), result.warnings(), metadataRow, fieldRows, result.ddl(),
                true, tableCreated);
    }

    // ------------------------------------------------------------------ entity

    private EntityModel buildEntity(EntityDraft draft, List<String> errors, List<String> warnings) throws Exception {
        EntityModel entity = new EntityModel();
        String name = draft.table_name();
        if (name == null || name.isBlank()) {
            errors.add("table_name is required");
        } else if (!IDENTIFIER.matcher(name).matches()) {
            errors.add("table_name '" + name + "' must match [a-z][a-z0-9_]* (max 63 chars)");
        } else if (MetadataUtils.isReserved(name)) {
            errors.add("table_name '" + name + "' is reserved by a system resource");
        } else if (catalog.isDeclared(name)) {
            errors.add("entity '" + name + "' already exists (drafts only create new entities)");
        }
        EntityIdentity.Builder identity = EntityIdentity.builder(entity.identity);
        identity.table_name = name;
        identity.metadata_group = draft.metadata_group();
        entity.identity = identity.build();

        EntityQueryFacet.Builder query = EntityQueryFacet.builder(draft.query());
        if (query.table_key == null || query.table_key.isBlank()) {
            query.table_key = UUID;
        }
        if (query.table_key_type == null || query.table_key_type.isBlank()) {
            query.table_key_type = "uuid";
        }
        if (!KEY_TYPES.contains(query.table_key_type)) {
            errors.add("query.table_key_type '" + query.table_key_type + "' must be one of " + KEY_TYPES);
        }
        entity.query = query.build();

        EntityAiFacet ai = draft.ai() == null ? EntityAiFacet.empty() : draft.ai();
        if (!ai.hasDescription()) {
            errors.add("ai.description is required: it is what AI tools show for this entity");
        }
        entity.ai = ai;
        entity.ui = draft.ui() == null ? EntityUiFacet.empty() : draft.ui();
        if (entity.ui.icon() == null) {
            warnings.add("ui.icon not set: the admin SPA shows a default icon");
        }
        return entity;
    }

    // ------------------------------------------------------------------ fields

    private List<FieldModel> buildFields(EntityDraft draft, EntityModel entity, List<String> errors,
                                         List<String> warnings) throws Exception {
        List<FieldModel> fields = new ArrayList<>();
        if (draft.fields() == null || draft.fields().isEmpty()) {
            errors.add("at least one field is required");
            return fields;
        }
        String key = entity.query.table_key();
        boolean generatedKey = !"userdefined".equals(entity.query.table_key_type());
        Set<String> names = new HashSet<>();
        int index = 0;
        for (FieldDraft fd : draft.fields()) {
            index++;
            String where = "fields[" + (index - 1) + "]";
            if (fd == null || fd.name() == null || fd.name().isBlank()) {
                errors.add(where + ".name is required");
                continue;
            }
            String name = fd.name();
            where = "field '" + name + "'";
            if (!IDENTIFIER.matcher(name).matches()) {
                errors.add(where + ": name must match [a-z][a-z0-9_]*");
            }
            if (!names.add(name)) {
                errors.add(where + ": duplicate name");
            }
            if (generatedKey && name.equals(key)) {
                errors.add(where + ": '" + key + "' is the generated " + entity.query.table_key_type()
                        + " key column, do not declare it as a field");
            }
            FieldModel model = new FieldModel();
            model.name = name;
            model.metadata_name = entity.identity.table_name();
            model.query = fd.query() == null ? FieldQueryFacet.empty() : fd.query();
            model.ai = fd.ai() == null ? FieldAiFacet.empty() : fd.ai();
            model.ui = deriveUi(fd, model, where, errors, warnings, index);
            fields.add(model);
        }
        checkKeyReferences(entity, names, errors);
        return fields;
    }

    private FieldUiFacet deriveUi(FieldDraft fd, FieldModel model, String where, List<String> errors,
                                  List<String> warnings, int viewIndex) throws Exception {
        FieldUiFacet.Builder ui = FieldUiFacet.builder(fd.ui());
        String semantic = model.ai.semantic_type();
        if (semantic == null || semantic.isBlank()) {
            semantic = FieldUiDerivation.semanticFromSqlType(model.query.sql_type());
            if (semantic != null) {
                FieldAiFacet.Builder ai = FieldAiFacet.builder(model.ai);
                ai.semantic_type = semantic;
                model.ai = ai.build();
            }
        }
        if (ui.type == null || ui.type.isBlank()) {
            if (semantic == null) {
                errors.add(where + ": set ai.semantic_type (one of " + String.join(", ", FieldUiDerivation.SEMANTIC_TYPES)
                        + "), a recognised query.sql_type, or ui.type");
                return ui.build();
            }
            try {
                String[] uiType = FieldUiDerivation.uiTypeFor(semantic);
                ui.type = uiType[0];
                ui.input_type = ui.input_type != null ? ui.input_type : uiType[1];
            } catch (IllegalArgumentException e) {
                errors.add(where + ": " + e.getMessage());
                return ui.build();
            }
        }
        if (ui.label == null || ui.label.isBlank()) {
            ui.label = FieldUiDerivation.label(model.name);
        }
        if (ui.view_index == 0) {
            ui.view_index = viewIndex;
        }
        if (fd.ui() == null) {
            // only without an explicit ui facet: a primitive false cannot be told apart from "not set"
            ui.show_in_list = FieldUiDerivation.shownInListByDefault(semantic);
        }
        if (WidgetType.SELECT == WidgetType.from(ui.type) && (ui.options == null || ui.options.isBlank())) {
            errors.add(where + ": enum/select needs ui.options (comma separated values)");
        }
        if (model.ai.description() == null || model.ai.description().isBlank()) {
            warnings.add(where + ": no ai.description, AI tools will only see the name");
        }
        checkJoin(ui.type, model, where, errors);
        deriveSearch(semantic, model);
        return ui.build();
    }

    private void checkJoin(String uiType, FieldModel model, String where, List<String> errors) throws Exception {
        WidgetType widget = WidgetType.from(uiType);
        boolean join = widget != null && widget.isJoinLike();
        if (!join) {
            return;
        }
        String target = model.query.join_table_name();
        if (target == null || target.isBlank()) {
            errors.add(where + ": a reference needs query.join_table_name (the referenced entity)");
            return;
        }
        Optional<EntityQueryView> targetView = catalog.queryView(target);
        if (targetView.isEmpty()) {
            errors.add(where + ": query.join_table_name '" + target + "' is not an existing entity");
            return;
        }
        if (model.query.join_table_key() == null || model.query.join_table_key().isBlank()) {
            FieldQueryFacet.Builder query = FieldQueryFacet.builder(model.query);
            query.join_table_key = targetView.get().tableKey();
            model.query = query.build();
        }
    }

    private static void deriveSearch(String semantic, FieldModel model) {
        if (!model.query.searchable() || model.query.search_condition() != null) {
            return;
        }
        FieldQueryFacet.Builder query = FieldQueryFacet.builder(model.query);
        if (FieldUiDerivation.textLike(semantic)) {
            query.search_condition = "contains";
            query.search_field_name = model.name + "_contains";
        } else {
            query.search_condition = "=";
            query.search_field_name = model.name;
        }
        model.query = query.build();
    }

    private static void checkKeyReferences(EntityModel entity, Set<String> names, List<String> errors) {
        EntityQueryFacet q = entity.query;
        if ("userdefined".equals(q.table_key_type()) && !names.contains(q.table_key())) {
            errors.add("query.table_key '" + q.table_key() + "' must be one of the fields for a userdefined key");
        }
        if ("slug".equals(q.table_key_type())
                && (q.table_key_addition() == null || !names.contains(q.table_key_addition()))) {
            errors.add("slug key: query.table_key_addition must name the field the slug is built from");
        }
        if (q.api_protected() && (q.username_field() == null || !names.contains(q.username_field()))) {
            errors.add("api_protected: query.username_field must name the owner field");
        }
    }

    private static String deriveSearchFields(List<FieldModel> fields) {
        String joined = String.join(",", fields.stream().filter(f -> f.query.searchable()).map(f -> f.name).toList());
        return joined.isEmpty() ? null : joined;
    }

    // ------------------------------------------------------------------ DDL

    private List<String> previewDdl(EntityModel entity, List<FieldModel> fields, List<String> errors) {
        try {
            // storage facets only: identity + query facet + resolved columns
            List<ColumnSpec> columns = fields.stream().map(f -> ColumnSpecs.of(f.name, f.query, f.ui)).toList();
            List<String> joinTables = new ArrayList<>();
            String sql = jdbcRepository.createTableSql(entity.identity, entity.query, columns, joinTables,
                    new ArrayList<Condition>());
            List<String> ddl = new ArrayList<>();
            ddl.add(sql.trim());
            ddl.addAll(joinTables);
            return ddl;
        } catch (Exception e) {
            errors.add("DDL generation failed: " + e.getMessage());
            return List.of();
        }
    }

    private void safeDelete(String table, String uuid) {
        try {
            jdbcRepository.delete(table, UUID, uuid);
        } catch (Exception e) {
            Log.errorf(e, "rollback: cannot delete %s %s", table, uuid);
        }
    }
}
