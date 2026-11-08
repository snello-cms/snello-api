package io.snello.model.facet;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.model.pojo.FieldAiProfile;
import io.snello.util.AiProfileUtils;

import java.util.List;

/**
 * AI / tool-facing description of a field.
 * <p>
 * Storage: {@link #description} is the {@code fielddefinitions.description} column; the rest lives in the JSON
 * column {@code fielddefinitions.ai_profile} (see {@link FieldAiProfile}).
 */
@RegisterForReflection
public record FieldAiFacet(
        String description,
        String ai_hint,
        String semantic_type,
        List<Object> examples) {

    public static FieldAiFacet empty() {
        return new FieldAiFacet(null, null, null, null);
    }

    /** Convenience for tests / drafts that only set the free-text description. */
    public static FieldAiFacet ofDescription(String description) {
        return new FieldAiFacet(description, null, null, null);
    }

    /** Builds the facet from the two persisted columns. */
    public static FieldAiFacet of(String description, String aiProfileJson) {
        FieldAiProfile profile = AiProfileUtils.parseField(aiProfileJson);
        return new FieldAiFacet(description, profile.hint(), profile.semantic_type(), profile.examples());
    }

    /** Value for the {@code ai_profile} column ({@code null} when empty). */
    public String toProfileJson() {
        return AiProfileUtils.toJson(new FieldAiProfile(ai_hint, semantic_type, examples));
    }

    public Builder builder() {
        return new Builder(this);
    }

    public static Builder builder(FieldAiFacet base) {
        return new Builder(base == null ? empty() : base);
    }

    public static final class Builder {
        public String description;
        public String ai_hint;
        public String semantic_type;
        public List<Object> examples;

        private Builder(FieldAiFacet base) {
            this.description = base.description;
            this.ai_hint = base.ai_hint;
            this.semantic_type = base.semantic_type;
            this.examples = base.examples;
        }

        public FieldAiFacet build() {
            return new FieldAiFacet(description, ai_hint, semantic_type, examples);
        }
    }
}
