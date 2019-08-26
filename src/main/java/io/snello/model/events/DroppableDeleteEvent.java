package io.snello.model.events;

public class DroppableDeleteEvent {

    public String uuid;

    public DroppableDeleteEvent(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String toString() {
        return "DroppableDeleteEvent{" +
                "uuid=" + uuid +
                '}';
    }
}
