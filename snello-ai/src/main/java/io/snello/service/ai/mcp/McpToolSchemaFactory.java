package io.snello.service.ai.mcp;

import dev.langchain4j.internal.JsonSchemaElementJsonUtils;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

@ApplicationScoped
public class McpToolSchemaFactory {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    @Inject
    JsonMapper mapper;

    public JsonObjectSchema fromJson(String parametersJson) {
        try {
            Map<String, Object> document = mapper.readValue(parametersJson, MAP);
            JsonSchemaElement element = JsonSchemaElementJsonUtils.fromMap(document);
            if (element instanceof JsonObjectSchema objectSchema) {
                return objectSchema;
            }
            throw new IllegalArgumentException("parameters_json must be a JSON Schema object");
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalArgumentException("Unreadable parameters_json: " + parametersJson, failure);
        }
    }
}
