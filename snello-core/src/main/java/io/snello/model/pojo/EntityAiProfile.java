package io.snello.model.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;
import java.util.Map;

/**
 * JSON content of {@code metadatas.ai_profile}: the AI-authoring data that has no dedicated column.
 * Unknown keys are ignored on read, so the shape can grow without ALTERs.
 *
 * @param summary      one-line purpose of the entity, richer than {@code description} when needed
 * @param synonyms     alternative names users may say ("customers" for {@code clients})
 * @param tool_visible {@code false} hides the entity from AI / MCP tools; {@code null} means visible
 * @param examples     sample payloads for create / update
 */
@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EntityAiProfile(
        String summary,
        List<String> synonyms,
        Boolean tool_visible,
        List<Map<String, Object>> examples) {

    public static final EntityAiProfile EMPTY = new EntityAiProfile(null, null, null, null);

    /** Helper, not a JSON property (Jackson would otherwise serialize it as "empty"). */
    @JsonIgnore
    public boolean isEmpty() {
        return (summary == null || summary.isBlank())
                && (synonyms == null || synonyms.isEmpty())
                && tool_visible == null
                && (examples == null || examples.isEmpty());
    }
}
