package io.snello.cms.model.events;

import io.snello.cms.model.Metadata;

import java.util.Map;

public class MetadataCreateUpdateEvent {
    public Metadata metadata;

    public MetadataCreateUpdateEvent(Metadata metadata) {
        this.metadata = metadata;
    }

    public MetadataCreateUpdateEvent(Map<String, Object> map) {
        this.metadata = new Metadata(map);
    }

    @Override
    public String toString() {
        return "MetadataCreateUpdateEvent{" +
                "metadata=" + metadata +
                '}';
    }
}
