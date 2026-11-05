package io.snello.model.events;

import io.snello.model.Action;

import java.util.Map;

public class ActionCreateUpdateEvent {
    public Action action;

    public ActionCreateUpdateEvent(Action action) {
        this.action = action;
    }

    public ActionCreateUpdateEvent(Map<String, Object> map) {
        this.action = new Action(map);
    }

    @Override
    public String toString() {
        return "ActionCreateUpdateEvent{" +
                "action=" + action +
                '}';
    }
}
