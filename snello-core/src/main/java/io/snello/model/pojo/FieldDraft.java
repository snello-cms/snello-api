package io.snello.model.pojo;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.model.facet.FieldAiFacet;
import io.snello.model.facet.FieldQueryFacet;
import io.snello.model.facet.FieldUiFacet;

/**
 * AI-authoring contract for one field: name + minimal query facet + AI facet; the UI facet is optional and
 * derived when absent (see {@code FieldUiDerivation}).
 * <p>
 * Minimal example: {@code {"name":"price","ai":{"description":"Unit price in EUR","semantic_type":"money"}}}.
 *
 * @param name  column name ({@code [a-z][a-z0-9_]*})
 * @param query optional: sql_type (fallback when no semantic_type), join_table_name for reference(s), searchable
 * @param ai    description (recommended), semantic_type (drives UI + DDL), ai_hint, examples
 * @param ui    optional override; when {@code ui.type} is set it wins over the derivation
 */
@RegisterForReflection
public record FieldDraft(
        String name,
        FieldQueryFacet query,
        FieldAiFacet ai,
        FieldUiFacet ui) {
}
