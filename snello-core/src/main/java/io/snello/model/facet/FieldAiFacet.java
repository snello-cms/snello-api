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
public class FieldAiFacet {

    public String description;
    public String ai_hint;
    public String semantic_type;
    public List<Object> examples;

    public FieldAiFacet() {
    }

    public FieldAiFacet(String description) {
        this.description = description;
    }

    /** Builds the facet from the two persisted columns. */
    public static FieldAiFacet of(String description, String aiProfileJson) {
        FieldAiFacet facet = new FieldAiFacet(description);
        FieldAiProfile profile = AiProfileUtils.parseField(aiProfileJson);
        facet.ai_hint = profile.hint();
        facet.semantic_type = profile.semantic_type();
        facet.examples = profile.examples();
        return facet;
    }

    /** Value for the {@code ai_profile} column ({@code null} when empty). */
    public String toProfileJson() {
        return AiProfileUtils.toJson(new FieldAiProfile(ai_hint, semantic_type, examples));
    }
}
