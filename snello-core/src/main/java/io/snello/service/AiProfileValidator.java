package io.snello.service;

import io.quarkus.logging.Log;
import io.snello.model.Metadata;
import io.snello.model.facet.EntityAiFacet;
import io.snello.model.pojo.EntityAiProfile;
import io.snello.model.pojo.FieldAiProfile;
import io.snello.util.AiProfileUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

import static io.snello.management.AppConstants.TABLE_NAME;

/**
 * Write-side checks for the AI facet, shared by metadata / field-definition REST create, update and import
 * (and by future AI write tools such as {@code propose_entity}).
 * <ul>
 *     <li>{@code ai_profile} may arrive as a JSON object or a JSON string; it is validated and stored as a
 *     canonical JSON string. Invalid → 400.</li>
 *     <li>{@code snello.ai.require-description=true}: a tool-visible entity must have a non-blank
 *     {@code description} (400 otherwise). Off by default so legacy SPA saves and imports keep working;
 *     when off, a missing description is only logged.</li>
 * </ul>
 */
@ApplicationScoped
public class AiProfileValidator {

    @ConfigProperty(name = "snello.ai.require-description", defaultValue = "false")
    boolean requireDescription;

    public void prepareMetadataRow(Map<String, Object> row) {
        normalize(row, EntityAiProfile.class);
        EntityAiFacet ai = new Metadata(row).aiFacet();
        Object tableName = row.get(TABLE_NAME);
        if (ai.tool_visible() && !ai.hasDescription()) {
            if (requireDescription) {
                Log.infof("metadata %s rejected: tool-visible without description (snello.ai.require-description=true)", tableName);
                throw new BadRequestException("description is required for tool-visible entity '" + tableName
                        + "' (set ai_profile.tool_visible=false to hide it from AI tools)");
            }
            Log.warnf("metadata %s is tool-visible without description: AI tools will see only its name", tableName);
        }
        Log.debugf("metadata %s ai facet tool_visible=%s summary=%s synonyms=%s examples=%d",
                tableName, ai.tool_visible(), ai.summary(), ai.synonyms(), ai.examples() == null ? 0 : ai.examples().size());
    }

    public void prepareFieldRow(Map<String, Object> row) {
        normalize(row, FieldAiProfile.class);
    }

    private static void normalize(Map<String, Object> row, Class<?> type) {
        try {
            AiProfileUtils.normalizeColumn(row, type);
        } catch (IllegalArgumentException e) {
            Log.infof("rejecting row name=%s: %s", row.get(TABLE_NAME) != null ? row.get(TABLE_NAME) : row.get("name"), e.getMessage());
            throw new BadRequestException(e.getMessage());
        }
    }
}
