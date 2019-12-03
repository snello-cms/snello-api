package io.snello.model.events;

public class ExtensionsDeleteEvent {

    public String uuid;

    public ExtensionsDeleteEvent(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String toString() {
        return "ExtensionsDeleteEvent{" +
                "uuid=" + uuid +
                '}';
    }
}
