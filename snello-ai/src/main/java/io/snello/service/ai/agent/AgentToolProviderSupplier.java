package io.snello.service.ai.agent;

import dev.langchain4j.service.tool.ToolProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.function.Supplier;

@ApplicationScoped
public class AgentToolProviderSupplier implements Supplier<ToolProvider> {

    @Inject
    CompositeToolProvider provider;

    @Override
    public ToolProvider get() {
        return provider;
    }
}
