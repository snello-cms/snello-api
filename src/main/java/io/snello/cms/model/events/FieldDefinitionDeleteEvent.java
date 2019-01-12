package io.snello.cms.model.events;

public class FieldDefinitionDeleteEvent {

    public String uuid;

    public FieldDefinitionDeleteEvent(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String toString() {
        return "FieldDefinitionDeleteEvent{" +
                "uuid=" + uuid +
                '}';
    }
}
