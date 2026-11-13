package io.snello.service.rs.ai;

import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.api.service.AbstractServiceRs;
import io.snello.management.SnelloPlane;
import io.snello.service.ApiService;
import io.snello.service.ai.AiRowDefaults;
import io.snello.model.ai.pojo.McpToolsUpdatedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

import static io.snello.management.AppConstants.*;

@Path(AI_MCP_TOOLS_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class AiMcpToolServiceRs extends AbstractServiceRs {

    @Inject
    Event<McpToolsUpdatedEvent> updated;

    @Inject
    public AiMcpToolServiceRs(ApiService apiService) {
        super(apiService, AI_MCP_TOOL, "name asc");
    }

    public AiMcpToolServiceRs() {
        super();
    }

    @Override
    protected void prePersist(Map<String, Object> object) throws Exception {
        if (object.get(NAME) == null) {
            throw new Exception(MSG_NAME_PARAM_IS_EMPTY);
        }
        AiRowDefaults.blankOnInsert(AI_MCP_TOOL, object, "description");
    }

    @Override
    protected void preUpdate(Map<String, Object> object) throws Exception {
        AiRowDefaults.blankOnUpdate(AI_MCP_TOOL, object, "description");
    }

    @Override
    protected void postPersist(Map<String, Object> object) {
        updated.fire(new McpToolsUpdatedEvent());
    }

    @Override
    protected void postUpdate(Map<String, Object> object) {
        updated.fire(new McpToolsUpdatedEvent());
    }

    @Override
    protected void postDelete(String id) {
        updated.fire(new McpToolsUpdatedEvent());
    }
}
