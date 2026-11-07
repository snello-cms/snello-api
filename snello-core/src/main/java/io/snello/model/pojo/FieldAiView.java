package io.snello.model.pojo;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.model.FieldDefinition;
import io.snello.model.facet.FieldAiFacet;

/**
 * AI-plane projection of a field: identity + AI facet.
 */
@RegisterForReflection
public record FieldAiView(String uuid, String name, FieldAiFacet ai) {

    public static FieldAiView of(FieldDefinition fd) {
        return new FieldAiView(fd.uuid, fd.name, fd.aiFacet());
    }
}
