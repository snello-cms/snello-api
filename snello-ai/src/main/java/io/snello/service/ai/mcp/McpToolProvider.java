package io.snello.service.ai.mcp;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkus.logging.Log;
import io.snello.model.ai.AiMcpTool;
import io.snello.service.ApiService;
import io.snello.service.ai.skills.CallerProject;
import io.snello.service.ai.skills.CallerRoles;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static io.snello.management.AppConstants.AI_MCP_TOOL;

@ApplicationScoped
public class McpToolProvider implements ToolProvider {

    @Inject
    CallerRoles caller;

    @Inject
    CallerProject project;

    @Inject
    ApiService apiService;

    @Inject
    McpToolSchemaFactory schemas;

    @Inject
    McpToolHttpExecutor http;

    private final AtomicReference<List<CachedTool>> cached = new AtomicReference<>(null);

    @Override
    public boolean isDynamic() {
        return true;
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        Set<String> roles = caller.get();
        String projectUuid = project.get();
        ToolProviderResult.Builder builder = ToolProviderResult.builder();
        for (CachedTool tool : definitions()) {
            if (!Objects.equals(projectUuid, tool.projectUuid())) {
                continue;
            }
            if (!allowed(tool.allowedRoles(), roles)) {
                continue;
            }
            builder.add(tool.specification(), tool.executor());
        }
        return builder.build();
    }

    public void invalidateCache() {
        cached.set(null);
        Log.info("MCP tool definition cache cleared");
    }

    public Optional<McpToolResultShape> resultShape(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return Optional.empty();
        }
        for (CachedTool tool : definitions()) {
            if (toolName.equals(tool.name())) {
                return Optional.of(new McpToolResultShape(tool.resultType(), tool.resultPath()));
            }
        }
        return Optional.empty();
    }

    private List<CachedTool> definitions() {
        List<CachedTool> current = cached.get();
        if (current != null) {
            return current;
        }
        List<CachedTool> loaded = loadAll();
        cached.compareAndSet(null, loaded);
        return cached.get();
    }

    private List<CachedTool> loadAll() {
        List<CachedTool> tools = new ArrayList<>();
        try {
            MultivaluedHashMap<String, String> params = new MultivaluedHashMap<>();
            params.putSingle("enabled", "true");
            List<Map<String, Object>> rows = apiService.list(AI_MCP_TOOL, params, "name asc", 500, 0);
            if (rows == null) {
                return List.of();
            }
            for (Map<String, Object> row : rows) {
                AiMcpTool entity = new AiMcpTool(row);
                try {
                    ToolSpecification specification = ToolSpecification.builder()
                            .name(entity.name)
                            .description(entity.description)
                            .parameters(schemas.fromJson(entity.parameters_json))
                            .build();
                    AiMcpTool snapshot = copyOf(entity);
                    ToolExecutor executor = (execution, memoryId) -> http.execute(snapshot, execution.arguments());
                    tools.add(new CachedTool(entity.name, entity.project_uuid, entity.allowedRoleSet(),
                            entity.result_type, entity.result_path, specification, executor));
                } catch (Exception failure) {
                    Log.warnf(failure, "Skipping MCP tool with unreadable definition: %s", entity.name);
                }
            }
        } catch (Exception failure) {
            Log.error("Failed to load MCP tools", failure);
        }
        return List.copyOf(tools);
    }

    private static boolean allowed(Set<String> allowedRoles, Set<String> callerRoles) {
        if (allowedRoles == null || allowedRoles.isEmpty()) {
            return true;
        }
        if (callerRoles == null || callerRoles.isEmpty()) {
            return false;
        }
        for (String role : allowedRoles) {
            for (String caller : callerRoles) {
                if (role.equalsIgnoreCase(caller)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static AiMcpTool copyOf(AiMcpTool source) {
        AiMcpTool copy = new AiMcpTool();
        copy.uuid = source.uuid;
        copy.project_uuid = source.project_uuid;
        copy.name = source.name;
        copy.description = source.description;
        copy.http_method = source.http_method;
        copy.url = source.url;
        copy.parameters_json = source.parameters_json;
        copy.arg_binding = source.arg_binding;
        copy.arg_names_json = source.arg_names_json;
        copy.fixed_query_json = source.fixed_query_json;
        copy.catalog_page_path_template = source.catalog_page_path_template;
        copy.result_type = source.result_type;
        copy.result_path = source.result_path;
        copy.authenticated = source.authenticated;
        copy.service_account_uuid = source.service_account_uuid;
        copy.allowed_roles = source.allowed_roles;
        copy.enabled = source.enabled;
        return copy;
    }

    record CachedTool(String name, String projectUuid, Set<String> allowedRoles, String resultType, String resultPath,
            ToolSpecification specification, ToolExecutor executor) {
    }
}
