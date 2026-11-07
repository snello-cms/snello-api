package io.snello.service.ai.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.snello.service.ai.tools.SnelloCmsTools;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService(
        modelName = "quick",
        tools = SnelloCmsTools.class,
        systemMessageProviderSupplier = AgentSystemMessageProvider.class,
        toolProviderSupplier = AgentToolProviderSupplier.class)
@ApplicationScoped
public interface AgentService {
    @UserMessage("{message}")
    Result<String> respond(@MemoryId String memoryId, @V("message") String message);
}
