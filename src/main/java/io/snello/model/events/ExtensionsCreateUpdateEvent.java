package io.snello.model.events;

import io.snello.model.Extension;

import java.util.Map;

public class ExtensionsCreateUpdateEvent {
    public Extension extension;

    public ExtensionsCreateUpdateEvent(Extension extension) {
        this.extension = extension;
    }

    public ExtensionsCreateUpdateEvent(Map<String, Object> map) {
        this.extension = new Extension(map);
    }

    @Override
    public String toString() {
        return "ExtensionsCreateUpdateEvent{" +
                "extension=" + extension +
                '}';
    }
}
