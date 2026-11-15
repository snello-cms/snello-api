package io.snello.service;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import io.snello.api.service.JdbcRepository;
import io.snello.management.SnelloPlane;
import io.snello.model.Layout;
import io.snello.model.Metadata;
import io.snello.util.JsonUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;

import static io.snello.management.AppConstants.LAYOUTS;

/**
 * Helpers for {@code layouts}: load by uuid and validate writes (page_type ↔ json kind, metadata exists).
 */
@ApplicationScoped
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class LayoutService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Inject
    JdbcRepository jdbcRepository;

    @Inject
    MetadataService metadataService;

    public Map<String, Object> findRow(String uuid) throws Exception {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        return jdbcRepository.fetch(null, LAYOUTS, "uuid", uuid);
    }

    public Layout find(String uuid) throws Exception {
        Map<String, Object> row = findRow(uuid);
        return row == null ? null : new Layout(row);
    }

    /**
     * Validates and normalizes a layout row for persist/update. Sets {@code updated_at}, lowercases
     * {@code page_type}, and fills {@code metadata_name} from the referenced metadata.
     */
    public void prepareWrite(Map<String, Object> object) throws Exception {
        if (object == null) {
            throw new BadRequestException("body is required");
        }
        Layout layout = new Layout(object);
        String name = layout.name == null ? null : layout.name.trim();
        if (name == null || name.isBlank()) {
            throw new BadRequestException("name is required");
        }
        object.put("name", name);

        String pageType = layout.page_type == null ? null : layout.page_type.trim().toLowerCase();
        if (pageType == null || !Layout.PAGE_TYPES.contains(pageType)) {
            throw new BadRequestException("page_type must be one of " + Layout.PAGE_TYPES);
        }
        object.put("page_type", pageType);

        String metadataUuid = layout.metadata_uuid == null ? null : layout.metadata_uuid.trim();
        if (metadataUuid == null || metadataUuid.isBlank()) {
            throw new BadRequestException("metadata_uuid is required");
        }
        Metadata metadata = findMetadataByUuid(metadataUuid);
        if (metadata == null) {
            throw new BadRequestException("metadata_uuid '" + metadataUuid + "' does not exist");
        }
        object.put("metadata_uuid", metadataUuid);
        object.put("metadata_name", metadata.table_name);

        Object rawJson = object.get("json_data");
        Map<String, Object> body;
        String json;
        try {
            if (rawJson instanceof Map<?, ?> mapBody) {
                @SuppressWarnings("unchecked")
                Map<String, Object> asMap = (Map<String, Object>) mapBody;
                body = asMap;
                json = JSON.writeValueAsString(body);
            } else if (rawJson instanceof String s && !s.isBlank()) {
                json = s;
                body = JsonUtils.fromJson(json);
            } else {
                throw new BadRequestException("json_data is required");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("json_data must be valid JSON: " + e.getMessage());
        }
        if (body == null) {
            throw new BadRequestException("json_data must be a JSON object");
        }
        Object kindRaw = body.get("kind");
        String kind = kindRaw == null ? null : kindRaw.toString().trim().toLowerCase();
        if (kind == null || kind.isBlank()) {
            throw new BadRequestException("json_data.kind is required");
        }
        String expected = Layout.expectedKind(pageType);
        if (!expected.equals(kind)) {
            throw new BadRequestException("page_type '" + pageType + "' requires json_data.kind '" + expected
                    + "', got '" + kind + "'");
        }
        if (body.get("version") == null) {
            throw new BadRequestException("json_data.version is required");
        }
        object.put("json_data", json);
        object.put("updated_at", Instant.now().toString());
        Log.infof("layout prepareWrite name=%s page_type=%s metadata=%s", name, pageType, metadata.table_name);
    }

    private Metadata findMetadataByUuid(String uuid) throws Exception {
        for (Metadata metadata : metadataService.metadataMap().values()) {
            if (metadata != null && uuid.equals(metadata.uuid)) {
                return metadata;
            }
        }
        return null;
    }
}
