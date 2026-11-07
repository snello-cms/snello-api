package io.snello.service.ai.skills;

import io.snello.model.ai.AiSkill;
import io.snello.service.ApiService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.snello.management.AppConstants.AI_SKILL;

@ApplicationScoped
public class SkillCatalog {

    @Inject
    ApiService apiService;

    @Inject
    CallerRoles caller;

    @Inject
    CallerProject project;

    public List<AiSkill> available() throws Exception {
        String projectUuid = project.get();
        if (projectUuid == null || projectUuid.isBlank()) {
            return List.of();
        }
        Set<String> roles = caller.get();
        MultivaluedHashMap<String, String> params = new MultivaluedHashMap<>();
        params.putSingle("project_uuid", projectUuid);
        params.putSingle("enabled", "true");
        List<Map<String, Object>> rows = apiService.list(AI_SKILL, params, "name asc", 200, 0);
        List<AiSkill> skills = new ArrayList<>();
        if (rows == null) {
            return skills;
        }
        for (Map<String, Object> row : rows) {
            AiSkill skill = new AiSkill(row);
            if (skill.allowedFor(roles)) {
                skills.add(skill);
            }
        }
        return skills;
    }

    public Optional<String> instructionsOf(String name) throws Exception {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return available().stream()
                .filter(skill -> name.equals(skill.name))
                .map(skill -> skill.instructions)
                .findFirst();
    }

    public String formatAvailable() {
        try {
            return available().stream()
                    .map(skill -> "- %s: %s".formatted(skill.name, skill.description))
                    .collect(Collectors.joining("\n"));
        } catch (Exception failure) {
            return "";
        }
    }
}
