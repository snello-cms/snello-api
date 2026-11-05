package io.snello.model.events;

public class ActionDeleteEvent {

    public String uuid;

    public ActionDeleteEvent(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String toString() {
        return "ActionDeleteEvent{" +
                "uuid=" + uuid +
                '}';
    }
}
