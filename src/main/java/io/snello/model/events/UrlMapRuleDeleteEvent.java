package io.snello.model.events;

public class UrlMapRuleDeleteEvent {

    public String uuid;

    public UrlMapRuleDeleteEvent(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String toString() {
        return "UrlMapRuleDeleteEvent{" +
                "uuid=" + uuid +
                '}';
    }
}
