package io.snello.cms.model.events;

import io.snello.cms.model.SelectQuery;

import java.util.Map;

public class SelectQueryCreateUpdateEvent {
    public SelectQuery selectQuery;

    public SelectQueryCreateUpdateEvent(SelectQuery selectQuery) {
        this.selectQuery = selectQuery;
    }

    public SelectQueryCreateUpdateEvent(Map<String, Object> map) {
        this.selectQuery = new SelectQuery(map);
    }

    @Override
    public String toString() {
        return "SelectQueryCreateUpdateEvent{" +
                "selectQuery=" + selectQuery +
                '}';
    }
}
