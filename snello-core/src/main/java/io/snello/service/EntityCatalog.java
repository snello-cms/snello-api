package io.snello.service;

import io.quarkus.logging.Log;
import io.snello.model.FieldDefinition;
import io.snello.model.Metadata;
import io.snello.model.entity.EntityModel;
import io.snello.model.pojo.EntityQueryView;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

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
@Singleton
public class EntityCatalog {

    @Inject
    MetadataService metadataService;

    private volatile Map<String, Metadata> builtFromMetadata;
    private volatile Map<String, Map<String, FieldDefinition>> builtFromFields;
    private volatile Map<String, EntityQueryView> queryViews = Map.of();

    /** Query facet + query-relevant fields of a metadata entity; empty when {@code name} is not a metadata. */
    public Optional<EntityQueryView> queryView(String name) throws Exception {
        if (name == null) {
            return Optional.empty();
        }
        EntityQueryView view = currentQueryViews().get(name);
        if (view == null) {
            Log.debugf("catalog queryView: no metadata for name=%s", name);
        }
        return Optional.ofNullable(view);
    }

    /** All query views of the current snapshot (sorted by entity name). */
    public Map<String, EntityQueryView> queryViews() throws Exception {
        return currentQueryViews();
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
        model.fields = metadataService.fielddefinitions(name);
        model.conditions = metadataService.conditions(name);
        return Optional.of(model);
    }

    /** True when {@code name} is a declared resource: a metadata entity or a named select query. */
    public boolean isDeclared(String name) throws Exception {
        return name != null
                && (metadataService.metadataMap().containsKey(name) || metadataService.selectqueryMap().containsKey(name));
    }

    private Map<String, EntityQueryView> currentQueryViews() throws Exception {
        Map<String, Metadata> metadataMap = metadataService.metadataMap();
        Map<String, Map<String, FieldDefinition>> fieldMap = metadataService.fielddefinitionsMap();
        if (metadataMap == builtFromMetadata && fieldMap == builtFromFields) {
            return queryViews;
        }
        synchronized (this) {
            if (metadataMap == builtFromMetadata && fieldMap == builtFromFields) {
                return queryViews;
            }
            Map<String, EntityQueryView> views = new TreeMap<>();
            for (Metadata metadata : metadataMap.values()) {
                if (metadata == null || metadata.table_name == null) {
                    continue;
                }
                Map<String, FieldDefinition> fields = metadata.uuid == null ? null : fieldMap.get(metadata.uuid);
                List<FieldDefinition> ordered = fields == null ? List.of() : new ArrayList<>(fields.values());
                if (!ordered.isEmpty()) {
                    ordered.sort(Comparator.comparingInt(fd -> fd.view_index));
                }
                views.put(metadata.table_name, EntityQueryView.of(metadata, ordered));
            }
            Map<String, EntityQueryView> published = Collections.unmodifiableMap(views);
            queryViews = published;
            builtFromFields = fieldMap;
            builtFromMetadata = metadataMap;
            Log.infof("catalog: rebuilt %d entity query views", published.size());
            return published;
        }
    }
}
