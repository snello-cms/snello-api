package io.snello.model.facet;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Stable identity / lifecycle of a logical entity (metadata row).
 * Storage remains the flat {@code metadatas} table.
 */
@RegisterForReflection
public class EntityIdentity {

    public String uuid;
    public String table_name;
    public String metadata_group;
    public boolean already_exist;
    public boolean created;

    public EntityIdentity() {
    }

    public EntityIdentity(String uuid, String table_name, String metadata_group,
                          boolean already_exist, boolean created) {
        this.uuid = uuid;
        this.table_name = table_name;
        this.metadata_group = metadata_group;
        this.already_exist = already_exist;
        this.created = created;
    }
}
