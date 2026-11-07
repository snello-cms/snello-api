package io.snello.model.pojo;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.model.FieldDefinition;
import io.snello.model.Metadata;
import io.snello.model.facet.EntityAiFacet;

import java.util.List;

/**
 * AI/MCP projection: entity AI facet + per-field AI views.
 */
@RegisterForReflection
public record EntityAiView(String tableName, EntityAiFacet ai, List<FieldAiView> fields) {

    public static EntityAiView of(Metadata metadata, List<FieldDefinition> fieldDefinitions) {
        List<FieldAiView> fields = fieldDefinitions == null
                ? List.of()
                : fieldDefinitions.stream().map(FieldAiView::of).toList();
        return new EntityAiView(
                metadata == null ? null : metadata.table_name,
                metadata == null ? new EntityAiFacet() : metadata.aiFacet(),
                fields);
    }
}
