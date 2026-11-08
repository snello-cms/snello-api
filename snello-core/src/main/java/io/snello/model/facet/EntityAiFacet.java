package io.snello.model.facet;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.model.pojo.EntityAiProfile;
import io.snello.util.AiProfileUtils;

import java.util.List;
import java.util.Map;

/**
 * AI / tool-facing profile of a metadata entity.
 * <p>
 * Storage: {@link #description} is the {@code metadatas.description} column; everything else lives in the single
 * JSON column {@code metadatas.ai_profile} (see {@link EntityAiProfile}), so new AI attributes need no ALTER.
 */
@RegisterForReflection
public record EntityAiFacet(
        String description,
        String summary,
        List<String> synonyms,
        Boolean tool_visible,
        List<Map<String, Object>> examples) {

    public EntityAiFacet {
        if (synonyms == null) {
            synonyms = List.of();
        }
        if (tool_visible == null) {
            tool_visible = true;
        }
    }

    public static EntityAiFacet empty() {
        return new EntityAiFacet(null, null, List.of(), true, null);
    }

    /** Convenience for tests / drafts that only set the free-text description. */
    public static EntityAiFacet ofDescription(String description) {
        return new EntityAiFacet(description, null, List.of(), true, null);
    }

    /** Builds the facet from the two persisted columns. */
    public static EntityAiFacet of(String description, String aiProfileJson) {
        EntityAiProfile profile = AiProfileUtils.parseEntity(aiProfileJson);
        return new EntityAiFacet(
                description,
                profile.summary(),
                profile.synonyms(),
                profile.tool_visible() == null || profile.tool_visible(),
                profile.examples());
    }

    /** Value for the {@code ai_profile} column ({@code null} when nothing beyond the defaults is set). */
    public String toProfileJson() {
        return AiProfileUtils.toJson(new EntityAiProfile(summary, synonyms,
                tool_visible ? null : Boolean.FALSE, examples));
    }

    public boolean hasDescription() {
        return description != null && !description.isBlank();
    }

    public Builder builder() {
        return new Builder(this);
    }

    public static Builder builder(EntityAiFacet base) {
        return new Builder(base == null ? empty() : base);
    }

    public static final class Builder {
        public String description;
        public String summary;
        public List<String> synonyms;
        public Boolean tool_visible;
        public List<Map<String, Object>> examples;

        private Builder(EntityAiFacet base) {
            this.description = base.description;
            this.summary = base.summary;
            this.synonyms = base.synonyms;
            this.tool_visible = base.tool_visible;
            this.examples = base.examples;
        }

        public EntityAiFacet build() {
            return new EntityAiFacet(description, summary, synonyms, tool_visible, examples);
        }
    }
}
