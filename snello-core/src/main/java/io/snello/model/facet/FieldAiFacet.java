package io.snello.model.facet;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * AI / tool-facing description of a field.
 * Extra structured data may later live in a JSON {@code ai_profile} column.
 */
@RegisterForReflection
public class FieldAiFacet {

    public String description;
    public String ai_hint;
    public String semantic_type;
    public String examples;
    /** Optional JSON blob when/if field-level {@code ai_profile} is added. */
    public String ai_profile;

    public FieldAiFacet() {
    }

    public FieldAiFacet(String description) {
        this.description = description;
    }
}
