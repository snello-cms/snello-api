package io.snello.cms.model.events;

public class SelectQueryDeleteEvent {

    public String uuid;

    public SelectQueryDeleteEvent(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String toString() {
        return "SelectQueryDeleteEvent{" +
                "uuid=" + uuid +
                '}';
    }
}
