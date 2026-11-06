package io.snello.model.pojo;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.model.FieldDefinition;
import io.snello.model.facet.FieldUiFacet;

/**
 * SPA-facing projection of a field: identity + UI facet only (no SQL / AI).
 */
@RegisterForReflection
public record FieldUiView(String uuid, String name, FieldUiFacet ui) {

    public static FieldUiView of(FieldDefinition fd) {
        return new FieldUiView(fd.uuid, fd.name, fd.uiFacet());
    }
}
