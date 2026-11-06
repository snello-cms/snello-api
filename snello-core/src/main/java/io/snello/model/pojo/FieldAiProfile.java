package io.snello.model.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * JSON content of {@code fielddefinitions.ai_profile}. Unknown keys are ignored on read.
 *
 * @param hint          extra guidance for the model ("ISO country code, uppercase")
 * @param semantic_type meaning beyond the SQL type (email, money, country_code, ...), used to derive UI defaults
 * @param examples      sample values
 */
@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldAiProfile(
        String hint,
        String semantic_type,
        List<Object> examples) {

    public static final FieldAiProfile EMPTY = new FieldAiProfile(null, null, null);

    /** Helper, not a JSON property (Jackson would otherwise serialize it as "empty"). */
    @JsonIgnore
    public boolean isEmpty() {
        return (hint == null || hint.isBlank())
                && (semantic_type == null || semantic_type.isBlank())
                && (examples == null || examples.isEmpty());
    }
}
