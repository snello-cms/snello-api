package io.snello.dsl;

import io.snello.model.FieldDefinition;
import io.snello.model.Metadata;

import java.util.ArrayList;
import java.util.List;

public class MetadataDsl {
    public String uuid;
    public Metadata metadata;
    public FieldDefinition fieldDefinition;
    public List<FieldDefinition> fieldDefinitions;

    public MetadataDsl() {
        this.metadata = new Metadata();
        this.fieldDefinitions = new ArrayList<>();
    }

    public MetadataDsl(Metadata metadata) {
        this.metadata = metadata;
        this.fieldDefinitions = new ArrayList<>();
    }

    public MetadataDsl rotate() {
        if (fieldDefinition != null)
            this.fieldDefinitions.add(this.fieldDefinition);
        return this;
    }

    public MetadataDsl uuid(String uuid) {
        this.uuid = uuid;
        this.metadata.uuid = uuid;
        return this;
    }
}
