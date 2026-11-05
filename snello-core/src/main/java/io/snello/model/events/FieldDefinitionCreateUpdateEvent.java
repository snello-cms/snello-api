package io.snello.model.events;

import io.snello.model.FieldDefinition;

import java.util.Map;

public class FieldDefinitionCreateUpdateEvent {
    public FieldDefinition fieldDefinition;

    public FieldDefinitionCreateUpdateEvent(FieldDefinition fieldDefinition) {
        this.fieldDefinition = fieldDefinition;
    }

    public FieldDefinitionCreateUpdateEvent(Map<String, Object> map) {
        this.fieldDefinition = new FieldDefinition(map);
    }

    @Override
    public String toString() {
        return "FieldDefinitionCreateUpdateEvent{" +
                "fieldDefinition=" + fieldDefinition +
                '}';
    }
}
