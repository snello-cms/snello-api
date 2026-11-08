package io.snello.service.ai.agent;

import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProvider;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.snello.model.ai.AiProject;
import io.snello.service.ai.skills.ProjectAccess;
import io.snello.service.ai.skills.SkillCatalog;
import io.snello.service.ai.wss.ActiveAguiRun;

import java.util.Optional;

public class AgentSystemMessageProvider implements SystemMessageProvider {
    private static final String BASE = """
            You are the virtual assistant of snello.io CMS.
            Answer only with data returned by the tools available in this conversation \
            (skills and MCP tools for the active project).
            Never invent facts, identifiers, or record UUIDs.
            Prefer activating a relevant skill before calling MCP tools when a skill matches the request.
            Embed frontend action tags when you mention records: [ACTION:OPEN:entity:uuid], [ACTION:NAVIGATE:/path].""";

    private static final String LANG_IT = """
            Always answer in Italian unless the user explicitly asks for another language in the same message.""";

    private static final String LANG_EN = """
            Always answer in English unless the user explicitly asks for another language in the same message.""";

    @Override
    public Optional<String> getSystemMessage(Object memoryId) {
        StringBuilder persona = new StringBuilder(BASE);
        String global = activeGlobalMessage();
        if (!global.isBlank()) {
            persona.append('\n').append(global.trim());
        }
        persona.append('\n').append(languageInstruction());
        String catalogue = availableSkills();
        if (catalogue.isBlank()) {
            return Optional.of(persona.toString());
        }
        return Optional.of("""
                %s

                You have access to the following skills:
                %s
                When the request relates to one of these skills, activate it with the \
                `activate_skill` tool before answering, and then follow its instructions.
                """.formatted(persona, catalogue));
    }

    private String languageInstruction() {
        return activeLanguageEnglish() ? LANG_EN : LANG_IT;
    }

    private boolean activeLanguageEnglish() {
        try (InstanceHandle<ActiveAguiRun> run = Arc.container().instance(ActiveAguiRun.class)) {
            if (!run.isAvailable()) {
                return false;
            }
            ActiveAguiRun active = run.get();
            return active.isBound() && "en".equalsIgnoreCase(active.language());
        } catch (Exception ignored) {
            return false;
        }
    }

    private String activeGlobalMessage() {
        try (InstanceHandle<ActiveAguiRun> run = Arc.container().instance(ActiveAguiRun.class)) {
            if (!run.isAvailable()) {
                return "";
            }
            ActiveAguiRun active = run.get();
            if (!active.isBound() || active.projectUuid() == null || active.projectUuid().isBlank()) {
                return "";
            }
            try (InstanceHandle<ProjectAccess> access = Arc.container().instance(ProjectAccess.class)) {
                if (!access.isAvailable()) {
                    return "";
                }
                Optional<AiProject> project = access.get().findByUuid(active.projectUuid());
                return project.map(p -> p.global_message == null ? "" : p.global_message).orElse("");
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    private String availableSkills() {
        try (InstanceHandle<SkillCatalog> catalog = Arc.container().instance(SkillCatalog.class)) {
            return catalog.isAvailable() ? catalog.get().formatAvailable() : "";
        } catch (Exception ignored) {
            return "";
        }
    }
}
