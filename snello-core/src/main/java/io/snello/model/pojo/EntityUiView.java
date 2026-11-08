package io.snello.model.pojo;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.model.FieldDefinition;
import io.snello.model.Metadata;
import io.snello.model.facet.EntityUiFacet;

import java.util.List;

/**
 * Control/SPA projection: entity UI facet + per-field UI views.
 */
@RegisterForReflection
public record EntityUiView(String tableName, EntityUiFacet ui, List<FieldUiView> fields) {

    public static EntityUiView of(Metadata metadata, List<FieldDefinition> fieldDefinitions) {
        List<FieldUiView> fields = fieldDefinitions == null
                ? List.of()
                : fieldDefinitions.stream().map(FieldUiView::of).toList();
        return new EntityUiView(
                metadata == null ? null : metadata.table_name,
                metadata == null ? EntityUiFacet.empty() : metadata.uiFacet(),
                fields);
    }
}
