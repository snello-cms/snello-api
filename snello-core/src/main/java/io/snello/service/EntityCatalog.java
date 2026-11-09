package io.snello.service;

import io.quarkus.logging.Log;
import io.snello.model.FieldDefinition;
import io.snello.model.Metadata;
import io.snello.model.entity.EntityModel;
import io.snello.model.entity.FieldModel;
import io.snello.model.pojo.EntityAiView;
import io.snello.model.pojo.EntityQueryView;
import io.snello.model.pojo.EntityUiView;
import io.snello.model.pojo.RowProtection;
import io.snello.model.SelectQuery;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Facet-typed read API over {@link MetadataService} (roadmap step 4.3).
 * <p>
 * The read path ({@code ApiService.list/count/fetch}, {@code ApiServiceRs}) consumes only {@link #queryView}:
 * immutable {@link EntityQueryView}s, built once per metadata snapshot and shared by all request threads. The
 * cache is keyed on the identity of {@code MetadataService}'s maps, which are replaced (never mutated) on every
 * reload, so a new snapshot — from CDI events on control or the checksum poll on query — rebuilds the views.
 */
@ApplicationScoped
public class EntityCatalog {

    @Inject
    MetadataService metadataService;

    private volatile Map<String, Metadata> builtFromMetadata;
    private volatile Map<String, Map<String, FieldDefinition>> builtFromFields;
    private volatile Map<String, EntityQueryView> queryViews = Map.of();
    private volatile Map<String, EntityUiView> uiViews = Map.of();
    private volatile Map<String, EntityAiView> aiViews = Map.of();

    /** Query facet + query-relevant fields of a metadata entity; empty when {@code name} is not a metadata. */
    public Optional<EntityQueryView> queryView(String name) throws Exception {
        if (name == null) {
            return Optional.empty();
        }
        EntityQueryView view = currentViews().query().get(name);
        if (view == null) {
            Log.debugf("catalog queryView: no metadata for name=%s", name);
        }
        return Optional.ofNullable(view);
    }

    /**
     * Row-level security of a readable resource: the metadata entity if {@code name} is one, otherwise the named
     * SelectQuery. Empty when {@code name} is neither.
     */
    public Optional<RowProtection> rowProtection(String name) throws Exception {
        if (name == null) {
            return Optional.empty();
        }
        Optional<EntityQueryView> view = queryView(name);
        if (view.isPresent()) {
            return Optional.of(new RowProtection(view.get().apiProtected(), view.get().usernameField()));
        }
        SelectQuery selectQuery = metadataService.selectqueryMap().get(name);
        if (selectQuery == null) {
            return Optional.empty();
        }
        Log.debugf("catalog rowProtection: selectquery=%s api_protected=%s username_field=%s", name,
                selectQuery.api_protected, selectQuery.username_field);
        return Optional.of(new RowProtection(selectQuery.api_protected, selectQuery.username_field));
    }

    /** UI facet + per-field UI views (control/SPA). Empty on the query plane when UI columns were not loaded. */
    public Optional<EntityUiView> uiView(String name) throws Exception {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(currentViews().ui().get(name));
    }

    /** AI facet + per-field AI views (control/MCP). */
    public Optional<EntityAiView> aiView(String name) throws Exception {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(currentViews().ai().get(name));
    }

    /** All query views of the current snapshot (sorted by entity name). */
    public Map<String, EntityQueryView> queryViews() throws Exception {
        return currentViews().query();
    }

    /**
     * Full facet composition (identity / query / UI / AI) of a metadata entity. Control-plane API: on the query
     * plane only the query facet is loaded, so the UI / AI facets would be empty.
     */
    public Optional<EntityModel> entity(String name) throws Exception {
        Metadata metadata = name == null ? null : metadataService.metadata(name);
        if (metadata == null) {
            return Optional.empty();
        }
        EntityModel model = EntityModel.from(metadata);
        model.fields = orderedFields(name);
        model.conditions = metadataService.conditions(name);
        return Optional.of(model);
    }

    /** Field models (query/ui/ai nested) for a metadata, ordered by {@code view_index}. */
    public List<FieldModel> fieldModels(String name) throws Exception {
        return orderedFields(name).stream().map(FieldModel::from).toList();
    }

    /** True when {@code name} is a declared resource: a metadata entity or a named select query. */
    public boolean isDeclared(String name) throws Exception {
        return name != null
                && (metadataService.metadataMap().containsKey(name) || metadataService.selectqueryMap().containsKey(name));
    }

    private List<FieldDefinition> orderedFields(String name) throws Exception {
        List<FieldDefinition> list = metadataService.fielddefinitions(name);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return list.stream()
                .sorted(Comparator
                        .comparingInt((FieldDefinition fd) -> fd.view_index)
                        .thenComparing(fd -> fd.name == null ? "" : fd.name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private record Snapshot(
            Map<String, EntityQueryView> query,
            Map<String, EntityUiView> ui,
            Map<String, EntityAiView> ai) {
    }

    private Snapshot currentViews() throws Exception {
        Map<String, Metadata> metadataMap = metadataService.metadataMap();
        Map<String, Map<String, FieldDefinition>> fieldMap = metadataService.fielddefinitionsMap();
        if (metadataMap == builtFromMetadata && fieldMap == builtFromFields) {
            return new Snapshot(queryViews, uiViews, aiViews);
        }
        synchronized (this) {
            if (metadataMap == builtFromMetadata && fieldMap == builtFromFields) {
                return new Snapshot(queryViews, uiViews, aiViews);
            }
            Map<String, EntityQueryView> q = new TreeMap<>();
            Map<String, EntityUiView> u = new TreeMap<>();
            Map<String, EntityAiView> a = new TreeMap<>();
            for (Metadata metadata : metadataMap.values()) {
                if (metadata == null || metadata.table_name == null) {
                    continue;
                }
                Map<String, FieldDefinition> fields = metadata.uuid == null ? null : fieldMap.get(metadata.uuid);
                List<FieldDefinition> ordered = fields == null ? List.of() : new ArrayList<>(fields.values());
                if (!ordered.isEmpty()) {
                    ordered.sort(Comparator.comparingInt(fd -> fd.view_index));
                }
                q.put(metadata.table_name, EntityQueryView.of(metadata, ordered));
                u.put(metadata.table_name, EntityUiView.of(metadata, ordered));
                a.put(metadata.table_name, EntityAiView.of(metadata, ordered));
            }
            queryViews = Collections.unmodifiableMap(q);
            uiViews = Collections.unmodifiableMap(u);
            aiViews = Collections.unmodifiableMap(a);
            builtFromFields = fieldMap;
            builtFromMetadata = metadataMap;
            Log.infof("catalog: rebuilt %d entity query/ui/ai views", q.size());
            return new Snapshot(queryViews, uiViews, aiViews);
        }
    }
}
