package io.snello.model.pojo;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.model.facet.EntityAiFacet;
import io.snello.model.facet.EntityQueryFacet;
import io.snello.model.facet.EntityUiFacet;

import java.util.List;

/**
 * AI-authoring contract for an entity (roadmap step 2): QueryFacet + AiFacet (+ optional UiFacet) + fields.
 * Validated, completed (derivations) and turned into flat {@code metadatas} / {@code fielddefinitions} rows by
 * {@code DraftService}; nothing is written until {@code apply}.
 * <p>
 * Minimal example:
 * <pre>{@code
 * {"table_name":"products",
 *  "ai":{"description":"Products sold in the shop","synonyms":["items"]},
 *  "fields":[{"name":"title","ai":{"description":"Product name","semantic_type":"text"}},
 *            {"name":"price","ai":{"semantic_type":"money"}}]}
 * }</pre>
 *
 * @param table_name     entity / table name ({@code [a-z][a-z0-9_]*}, not reserved, not existing)
 * @param metadata_group optional taxonomy
 * @param query          optional: table_key (default {@code uuid}), table_key_type (default {@code uuid}),
 *                       table_key_addition (slug source), order_by, api_protected + username_field
 * @param ai             required {@code description}; summary, synonyms, tool_visible, examples
 * @param ui             optional: icon, tab_groups, search_fields (derived from searchable fields when absent)
 * @param fields         at least one field
 */
@RegisterForReflection
public record EntityDraft(
        String table_name,
        String metadata_group,
        EntityQueryFacet query,
        EntityAiFacet ai,
        EntityUiFacet ui,
        List<FieldDraft> fields) {
}
