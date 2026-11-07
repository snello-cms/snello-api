package io.snello.service.ai.mcp;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class McpToolCacheListener {

    @Inject
    McpToolProvider toolProvider;

    void onToolUpdate(@Observes McpToolsUpdatedEvent event) {
        toolProvider.invalidateCache();
    }
}
