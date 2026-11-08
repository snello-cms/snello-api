package io.snello.service.ai.skills;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.snello.model.ai.AiMcpTool;
import io.snello.model.ai.AiProject;
import io.snello.model.ai.AiServiceAccount;
import io.snello.model.ai.AiSkill;
import io.snello.model.ai.AiUiCard;
import io.snello.service.ApiService;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedHashMap;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.snello.management.AppConstants.AI_MCP_TOOL;
import static io.snello.management.AppConstants.AI_PROJECT;
import static io.snello.management.AppConstants.AI_SERVICE_ACCOUNT;
import static io.snello.management.AppConstants.AI_SKILL;
import static io.snello.management.AppConstants.AI_UI_CARD;
import static io.snello.management.AppConstants.UUID;

@ApplicationScoped
public class AiProjectSeeder {

    private static final String DEFAULT_GLOBAL_MESSAGE = """
            You are the virtual assistant of snello.io CMS.
            Answer only with data returned by the tools available in this conversation.
            Never invent facts, identifiers, or record UUIDs. If the tools return nothing, say so.
            Before any data entry, discover the entity schema with getEntitySchema.
            When listing records, respect pagination (limit 10) and use loadMoreRecords when the user asks for more.""";

    @Inject
    ApiService apiService;

    /** After {@code SchemaBootstrapService} (@Priority 100) so {@code ai_*} tables exist. */
    void seed(@Observes @Priority(200) StartupEvent ignored) {
        try {
            AiProject project = ensureDefaultProject();
            backfillProjectUuid(project.uuid);
            refreshRolesCsv(project);
            Log.infof("AI project ready: %s (%s)", project.name, project.uuid);
        } catch (Exception failure) {
            Log.error("AI project seeder failed", failure);
        }
    }

    private AiProject ensureDefaultProject() throws Exception {
        Map<String, Object> existing = apiService.fetch(null, AI_PROJECT, AiProjectIds.SNELLO_CMS, UUID);
        if (existing != null) {
            return new AiProject(existing);
        }
        MultivaluedHashMap<String, String> byName = new MultivaluedHashMap<>();
        byName.putSingle("name", "snello.cms");
        List<Map<String, Object>> named = apiService.list(AI_PROJECT, byName, null, 1, 0);
        if (named != null && !named.isEmpty()) {
            return new AiProject(named.getFirst());
        }
        AiProject project = new AiProject();
        project.uuid = AiProjectIds.SNELLO_CMS;
        project.name = "snello.cms";
        project.description = "Snello CMS assistant";
        project.global_message = DEFAULT_GLOBAL_MESSAGE;
        project.sso_group = "";
        project.roles = "";
        apiService.create(AI_PROJECT, project.toMap(), UUID);
        return project;
    }

    private void backfillProjectUuid(String projectUuid) throws Exception {
        int tools = backfillTable(AI_MCP_TOOL, projectUuid);
        int skills = backfillTable(AI_SKILL, projectUuid);
        int cards = backfillTable(AI_UI_CARD, projectUuid);
        int accounts = backfillTable(AI_SERVICE_ACCOUNT, projectUuid);
        if (tools + skills + cards + accounts > 0) {
            Log.infof("Backfilled project_uuid=%s tools=%d skills=%d cards=%d serviceAccounts=%d",
                    projectUuid, tools, skills, cards, accounts);
        }
    }

    private int backfillTable(String table, String projectUuid) throws Exception {
        List<Map<String, Object>> rows = apiService.list(table, new MultivaluedHashMap<>(), null, 500, 0);
        if (rows == null) {
            return 0;
        }
        int updated = 0;
        for (Map<String, Object> row : rows) {
            Object pu = row.get("project_uuid");
            if (pu != null && !pu.toString().isBlank()) {
                continue;
            }
            Object id = row.get(UUID);
            if (id == null) {
                continue;
            }
            row.put("project_uuid", projectUuid);
            apiService.merge(table, row, id.toString(), UUID);
            updated++;
        }
        return updated;
    }

    private void refreshRolesCsv(AiProject project) throws Exception {
        if (project.roles != null && !project.roles.isBlank()) {
            return;
        }
        Set<String> roles = new LinkedHashSet<>();
        MultivaluedHashMap<String, String> params = new MultivaluedHashMap<>();
        params.putSingle("project_uuid", project.uuid);
        List<Map<String, Object>> toolRows = apiService.list(AI_MCP_TOOL, params, null, 500, 0);
        if (toolRows != null) {
            for (Map<String, Object> row : toolRows) {
                roles.addAll(new AiMcpTool(row).allowedRoleSet());
            }
        }
        List<Map<String, Object>> skillRows = apiService.list(AI_SKILL, params, null, 500, 0);
        if (skillRows != null) {
            for (Map<String, Object> row : skillRows) {
                roles.addAll(new AiSkill(row).allowedRoleSet());
            }
        }
        if (roles.isEmpty()) {
            return;
        }
        project.roles = String.join(",", roles);
        apiService.merge(AI_PROJECT, project.toMap(), project.uuid, UUID);
    }
}
