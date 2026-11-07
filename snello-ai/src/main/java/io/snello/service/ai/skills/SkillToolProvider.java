package io.snello.service.ai.skills;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@ApplicationScoped
public class SkillToolProvider implements ToolProvider {
    private static final String TOOL = "activate_skill";

    @Inject
    SkillCatalog catalog;

    @Inject
    JsonMapper mapper;

    @Override
    public boolean isDynamic() {
        return true;
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        List<String> names;
        try {
            names = catalog.available().stream().map(skill -> skill.name).toList();
        } catch (Exception failure) {
            Log.warn("Could not load skill catalogue", failure);
            return ToolProviderResult.builder().build();
        }
        if (names.isEmpty()) {
            return ToolProviderResult.builder().build();
        }
        ToolSpecification specification = ToolSpecification.builder()
                .name(TOOL)
                .description("Load the instructions of a skill, then follow them to answer.")
                .parameters(JsonObjectSchema.builder()
                        .addEnumProperty("name", names, "The skill to activate")
                        .required("name")
                        .build())
                .build();
        return ToolProviderResult.builder()
                .add(specification, (execution, memoryId) -> activate(execution.arguments()))
                .build();
    }

    private String activate(String arguments) {
        String name;
        try {
            JsonNode parsed = mapper.readTree(arguments);
            name = parsed.path("name").asText("");
        } catch (Exception failure) {
            Log.warnf(failure, "Unreadable activate_skill arguments: %s", arguments);
            return "The skill could not be activated: the arguments were not readable.";
        }
        try {
            return catalog.instructionsOf(name)
                    .orElseGet(() -> {
                        Log.infof("Refused activation of skill: %s", name);
                        return "There is no skill named '%s' available to you.".formatted(name);
                    });
        } catch (Exception failure) {
            Log.warnf(failure, "Skill activation failed: %s", name);
            return "The skill could not be activated.";
        }
    }
}
