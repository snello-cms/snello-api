package io.snello.model.facet;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Stable identity / lifecycle of a logical entity (metadata row).
 * Storage remains the flat {@code metadatas} table.
 */
@RegisterForReflection
public record EntityIdentity(
        String uuid,
        String table_name,
        String metadata_group,
        Boolean already_exist,
        Boolean created) {

    public EntityIdentity {
        if (already_exist == null) {
            already_exist = false;
        }
        if (created == null) {
            created = false;
        }
    }

    public static EntityIdentity empty() {
        return new EntityIdentity(null, null, null, false, false);
    }

    public Builder builder() {
        return new Builder(this);
    }

    public static Builder builder(EntityIdentity base) {
        return new Builder(base == null ? empty() : base);
    }

    public static final class Builder {
        public String uuid;
        public String table_name;
        public String metadata_group;
        public Boolean already_exist;
        public Boolean created;

        private Builder(EntityIdentity base) {
            this.uuid = base.uuid;
            this.table_name = base.table_name;
            this.metadata_group = base.metadata_group;
            this.already_exist = base.already_exist;
            this.created = base.created;
        }

        public EntityIdentity build() {
            return new EntityIdentity(uuid, table_name, metadata_group, already_exist, created);
        }
    }
}
