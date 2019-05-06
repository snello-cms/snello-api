package io.snello.cms.model.events;

public class ConditionDeleteEvent {

    public String uuid;

    public ConditionDeleteEvent(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String toString() {
        return "ConditionDeleteEvent{" +
                "uuid=" + uuid +
                '}';
    }
}
