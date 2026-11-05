package io.snello.model.events;

public class AiToolDeleteEvent {

    public String uuid;

    public AiToolDeleteEvent(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String toString() {
        return "AiToolDeleteEvent{" +
                "uuid=" + uuid +
                '}';
    }
}
