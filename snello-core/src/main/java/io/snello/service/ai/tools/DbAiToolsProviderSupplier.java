package io.snello.service.ai.tools;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkus.arc.Arc;
import io.quarkus.logging.Log;
import io.snello.api.service.JdbcRepository;
import io.snello.model.AiTool;
import io.snello.service.MetadataService;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@ApplicationScoped
public class DbAiToolsProviderSupplier implements Supplier<ToolProvider> {

    @Override
    public ToolProvider get() {
        return new DbAiToolsProvider(
                Arc.container().instance(MetadataService.class).get(),
                Arc.container().instance(JdbcRepository.class).get(),
                Arc.container().instance(JsonMapper.class).get());
    }

    static final class DbAiToolsProvider implements ToolProvider {

        private static final TypeReference<LinkedHashMap<String, Object>> ARGUMENTS_TYPE = new TypeReference<>() {
        };

        private final MetadataService metadataService;
        private final JdbcRepository jdbcRepository;
        private final JsonMapper objectMapper;

        DbAiToolsProvider(MetadataService metadataService, JdbcRepository jdbcRepository, JsonMapper objectMapper) {
            this.metadataService = metadataService;
            this.jdbcRepository = jdbcRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public ToolProviderResult provideTools(ToolProviderRequest request) {
            ToolProviderResult.Builder builder = ToolProviderResult.builder();
            try {
                for (AiTool aiTool : metadataService.aiToolsMap().values()) {
                    if (!isUsable(aiTool)) {
                        continue;
                    }
                    builder.add(toSpecification(aiTool), toExecutor(aiTool));
                }
            } catch (Exception e) {
                Log.error("Error building AI tools from database", e);
            }
            return builder.build();
        }

        private boolean isUsable(AiTool aiTool) {
            return aiTool != null
                    && aiTool.active
                    && aiTool.name != null
                    && !aiTool.name.isBlank()
                    && aiTool.sql_query != null
                    && !aiTool.sql_query.isBlank();
        }

        private ToolSpecification toSpecification(AiTool aiTool) {
            String toolName = toolName(aiTool);
            ToolSpecification.Builder builder = ToolSpecification.builder()
                    .name(toolName)
                    .description(aiTool.description == null || aiTool.description.isBlank()
                            ? "Dynamic tool loaded from ai_tools"
                            : aiTool.description)
                    .parameters(toSchema(aiTool))
                    .addMetadata("uuid", aiTool.uuid == null ? "" : aiTool.uuid)
                    .addMetadata("source", "ai_tools");
            return builder.build();
        }

        private JsonObjectSchema toSchema(AiTool aiTool) {
            if (aiTool.parameters_schema == null || aiTool.parameters_schema.isBlank()) {
                return JsonObjectSchema.builder().build();
            }
            try {
                return objectMapper.readValue(aiTool.parameters_schema, JsonObjectSchema.class);
            } catch (Exception e) {
                Log.error("Invalid parameters_schema for ai_tool: " + aiTool.name, e);
                return JsonObjectSchema.builder().build();
            }
        }

        private ToolExecutor toExecutor(AiTool aiTool) {
            return (toolExecutionRequest, memoryId) -> {
                try {
                    Map<String, Object> arguments = parseArguments(toolExecutionRequest);
                    List<Map<String, Object>> rows = executeSelect(aiTool, arguments);
                    return objectMapper.writeValueAsString(rows);
                } catch (Exception e) {
                    Log.error("Error executing ai_tool: " + aiTool.name, e);
                    return "Error executing ai tool '" + toolName(aiTool) + "': " + e.getMessage();
                }
            };
        }

        private Map<String, Object> parseArguments(ToolExecutionRequest toolExecutionRequest) throws Exception {
            if (toolExecutionRequest.arguments() == null || toolExecutionRequest.arguments().isBlank()) {
                return Collections.emptyMap();
            }
            return objectMapper.readValue(toolExecutionRequest.arguments(), ARGUMENTS_TYPE);
        }

        private List<Map<String, Object>> executeSelect(AiTool aiTool, Map<String, Object> arguments) throws Exception {
            List<Map<String, Object>> rows = new ArrayList<>();
            try (Connection connection = jdbcRepository.getConnection();
                 PreparedStatement preparedStatement = connection.prepareStatement(aiTool.sql_query)) {
                bindArguments(preparedStatement, orderedValues(aiTool, arguments));
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    ResultSetMetaData metaData = resultSet.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    while (resultSet.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int index = 1; index <= columnCount; index++) {
                            row.put(metaData.getColumnLabel(index), resultSet.getObject(index));
                        }
                        rows.add(row);
                    }
                }
            }
            return rows;
        }

        private List<Object> orderedValues(Map<String, Object> arguments) {
            return new ArrayList<>(arguments.values());
        }

        private List<Object> orderedValues(AiTool aiTool, Map<String, Object> arguments) throws Exception {
            if (arguments.isEmpty()) {
                return Collections.emptyList();
            }
            if (aiTool.parameters_schema == null || aiTool.parameters_schema.isBlank()) {
                return orderedValues(arguments);
            }

            JsonNode root = objectMapper.readTree(aiTool.parameters_schema);
            JsonNode properties = root.get("properties");
            if (properties == null || !properties.isObject()) {
                return orderedValues(arguments);
            }

            List<Object> values = new ArrayList<>();
            for (String fieldName : properties.propertyNames()) {
                if (arguments.containsKey(fieldName)) {
                    values.add(arguments.get(fieldName));
                }
            }

            if (values.size() == arguments.size()) {
                return values;
            }

            for (Map.Entry<String, Object> entry : arguments.entrySet()) {
                if (!properties.has(entry.getKey())) {
                    values.add(entry.getValue());
                }
            }
            return values;
        }

        private void bindArguments(PreparedStatement preparedStatement, List<Object> values) throws Exception {
            for (int index = 0; index < values.size(); index++) {
                preparedStatement.setObject(index + 1, values.get(index));
            }
        }

        private String toolName(AiTool aiTool) {
            if (aiTool.method_name != null && !aiTool.method_name.isBlank()) {
                return aiTool.method_name;
            }
            return aiTool.name == null ? "ai_tool" : aiTool.name.replaceAll("[^a-zA-Z0-9_]", "_");
        }
    }
}