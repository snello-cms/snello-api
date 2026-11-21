package io.snello.model.pojo;

import io.quarkus.runtime.annotations.RegisterForReflection;

/** One physical table in the introspect catalog. */
@RegisterForReflection
public record IntrospectTableEntry(String name, boolean referenced) {
}
