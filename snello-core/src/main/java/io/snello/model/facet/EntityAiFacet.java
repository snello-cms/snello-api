package io.snello.model.facet;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * AI / tool-facing descriptive profile for a metadata entity.
 * Extra structured AI data may later live in a JSON {@code ai_profile} column;
 * for now only {@link #description} is persisted on {@code metadatas}.
 */
@RegisterForReflection
public class EntityAiFacet {

    public String description;
    /** Optional JSON blob when/if {@code ai_profile} column is added. */
    public String ai_profile;
    public boolean tool_visible = true;

    public EntityAiFacet() {
    }

    public EntityAiFacet(String description) {
        this.description = description;
    }

    public EntityAiFacet(String description, String ai_profile, boolean tool_visible) {
        this.description = description;
        this.ai_profile = ai_profile;
        this.tool_visible = tool_visible;
    }
}
