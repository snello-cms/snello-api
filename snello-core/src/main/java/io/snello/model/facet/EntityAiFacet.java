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
public class EntityAiFacet {

    public String description;
    public String summary;
    public List<String> synonyms;
    /** Exposed to AI / MCP tools ({@code listEntities}, schema, data tools). */
    public boolean tool_visible = true;
    /** Sample create / update payloads. */
    public List<Map<String, Object>> examples;

    public EntityAiFacet() {
    }

    public EntityAiFacet(String description) {
        this.description = description;
    }

    /** Builds the facet from the two persisted columns. */
    public static EntityAiFacet of(String description, String aiProfileJson) {
        EntityAiFacet facet = new EntityAiFacet(description);
        EntityAiProfile profile = AiProfileUtils.parseEntity(aiProfileJson);
        facet.summary = profile.summary();
        facet.synonyms = profile.synonyms();
        facet.tool_visible = profile.tool_visible() == null || profile.tool_visible();
        facet.examples = profile.examples();
        return facet;
    }

    /** Value for the {@code ai_profile} column ({@code null} when nothing beyond the defaults is set). */
    public String toProfileJson() {
        return AiProfileUtils.toJson(new EntityAiProfile(summary, synonyms,
                tool_visible ? null : Boolean.FALSE, examples));
    }

    public boolean hasDescription() {
        return description != null && !description.isBlank();
    }
}
