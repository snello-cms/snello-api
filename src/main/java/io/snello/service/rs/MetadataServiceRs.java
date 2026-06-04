package io.snello.service.rs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.api.service.AbstractServiceRs;
import io.snello.model.Metadata;
import io.snello.model.events.MetadataCreateUpdateEvent;
import io.snello.model.events.MetadataDeleteEvent;
import io.snello.model.pojo.JsonFormData;
import io.snello.service.ApiService;
import io.snello.util.MetadataUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.snello.management.AppConstants.*;
import static jakarta.ws.rs.core.Response.ok;
import static jakarta.ws.rs.core.Response.serverError;

@Path(METADATA_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
public class MetadataServiceRs extends AbstractServiceRs {
    private static String table = METADATAS;
    private static final Set<String> IMPORTABLE_METADATA_COLUMNS = new HashSet<>(List.of(
            UUID,
            TABLE_NAME,
            "icon",
            "select_fields",
            "search_fields",
            "description",
            "alias_table",
            "alias_condition",
            "table_key",
            "table_key_type",
            "table_key_addition",
            "creation_query",
            "order_by",
            "metadata_group",
            "tab_groups",
            "already_exist",
            "api_protected",
            "username_field",
            "calendar_enabled",
            "calendar_field",
            "calendar_label",
            CREATED
    ));

    @Inject
    Event<MetadataCreateUpdateEvent> eventCreateUpdatePublisher;
    @Inject
    Event<MetadataDeleteEvent> eventDeletePublisher;


    @Inject
    MetadataServiceRs(ApiService apiService) {
        super(apiService, METADATAS, "table_name asc");
    }

    public MetadataServiceRs() {
    }

    @Override
    protected void postUpdate(Map<String, Object> map) {
        eventCreateUpdatePublisher.fireAsync(new MetadataCreateUpdateEvent(map));
    }

    @Override
    protected void postDelete(String id) {
        eventDeletePublisher.fireAsync(new MetadataDeleteEvent(id));
    }

    @Override
    protected void postPersist(Map<String, Object> map) {
        eventCreateUpdatePublisher.fireAsync(new MetadataCreateUpdateEvent(map));
    }

    @GET
    @Path("/{uuid}/create")
    public Response createTable(@PathParam("uuid") @NotNull String uuid) throws Exception {
        getApiService().createMetadataTable(uuid);
        Map<String, Object> updateMetadataMap = new HashMap<>();
        updateMetadataMap.put(CREATED, true);
        getApiService().merge(METADATAS, updateMetadataMap, uuid, UUID);
        return ok(getApiService().fetch(null, table, uuid, UUID)).build();
    }

    @GET
    @Path("/{uuid}/delete")
    public Response deleteTable(@PathParam("uuid") @NotNull String uuid) throws Exception {
        boolean result = getApiService().deleteTable(uuid);
        if (result) {
            Map<String, Object> updateMetadataMap = new HashMap<>();
            updateMetadataMap.put(CREATED, false);
            getApiService().merge(METADATAS, updateMetadataMap, uuid, UUID);
        }
        return ok(getApiService().fetch(null, table, uuid, UUID)).build();
    }

    @GET
    @Path("/{uuid}/truncate")
    public Response truncateTable(@PathParam("uuid") @NotNull String uuid) throws Exception {
        getApiService().truncateTable(uuid);
        return ok(getApiService().fetch(null, table, uuid, UUID)).build();
    }

    @Override
    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) throws Exception {
        preDelete(id);
        boolean result = getApiService().delete(table, id, UUID);
        boolean deleteFieldDefinitionsByMetadataUuid = getApiService().deleteFieldDefinitionsByMetadataUuid(id);
        if (result && deleteFieldDefinitionsByMetadataUuid) {
            postDelete(id);
            return ok().build();
        }
        return serverError().build();
    }

    @Override
    protected void preUpdate(Map<String, Object> object) throws Exception {
        if (object.get(TABLE_NAME) == null) {
            throw new Exception(MSG_TABLE_NAME_IS_EMPTY);
        }
        if (MetadataUtils.isReserved(object.get(TABLE_NAME))) {
            throw new Exception(MSG_TABLE_NAME_IS_EMPTY);
        }
    }

    @Override
    protected void prePersist(Map<String, Object> object) throws Exception {
        if (object.get(TABLE_NAME) == null) {
            throw new Exception(MSG_TABLE_NAME_IS_EMPTY);
        }
        if (MetadataUtils.isReserved(object.get(TABLE_NAME))) {
            throw new Exception(MSG_TABLE_NAME_IS_EMPTY);
        }
    }

    @GET
    @Path("/groups")
    public Response groups() throws Exception {
        List<Map<String, Object>> rows = getApiService().list(
                "SELECT DISTINCT metadata_group FROM metadatas WHERE metadata_group IS NOT NULL ORDER BY metadata_group");
        List<String> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object val = row.get("metadata_group");
            if (val != null) {
                result.add(val.toString());
            }
        }
        return ok(result).build();
    }

    @POST
    @Path("/export")
    public Response export(Map<String, Object> body) throws Exception {
        @SuppressWarnings("unchecked")
        List<String> uuids = (List<String>) body.get("metadatas");
        if (uuids == null || uuids.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("metadatas list is required").build();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (String uuid : uuids) {
            Map<String, Object> raw = getApiService().fetch(null, METADATAS, uuid, UUID);
            if (raw != null) {
                String tableName = (String) raw.get(TABLE_NAME);
                Metadata full = getApiService().metadataWithFields(tableName);
                if (full != null) {
                    result.add(Map.of("metadata", full, "fields", full.fields != null ? full.fields : List.of()));
                }
            }
        }
        String filename = "metadatas-export.json";
        return Response.ok(Map.of("metadatas", result))
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .build();
    }

    @POST
    @Path("/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importMetadatas(@BeanParam JsonFormData formData) throws Exception {
        if (formData == null || formData.data == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("missing multipart form field 'file'")
                    .build();
        }

        ObjectMapper mapper = new ObjectMapper();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue((InputStream) formData.data, Map.class);
        return importMetadatasInternal(body);
    }

    @POST
    @Path("/import")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response importMetadatasJson(Map<String, Object> body) throws Exception {
        return importMetadatasInternal(body);
    }

    private Response importMetadatasInternal(Map<String, Object> body) throws Exception {
        if (body == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("request body is empty").build();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> metadatasList = (List<Map<String, Object>>) body.get("metadatas");
        if (metadatasList == null || metadatasList.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("metadatas list is empty or missing").build();
        }
        // Fase 1: verifica che nessun metadata esista già
        for (Map<String, Object> entry : metadatasList) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metaMap = (Map<String, Object>) entry.get("metadata");
            if (metaMap == null) {
                return Response.status(Response.Status.BAD_REQUEST).entity("invalid entry: missing 'metadata' object").build();
            }
            String tableName = (String) metaMap.get(TABLE_NAME);
            if (tableName == null || tableName.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST).entity("metadata missing table_name").build();
            }
            if (getApiService().metadata(tableName) != null) {
                return Response.status(Response.Status.CONFLICT)
                        .entity("metadata already exists: " + tableName).build();
            }
        }
        // Fase 2: salva i metadati e le field definitions
        List<Map<String, Object>> saved = new ArrayList<>();
        for (Map<String, Object> entry : metadatasList) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metaMap = (Map<String, Object>) entry.get("metadata");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> fieldsList = (List<Map<String, Object>>) entry.get("fields");
            Map<String, Object> metadataToPersist = filterImportableMetadataColumns(metaMap);

            // Genera UUID per il metadata se mancante
            if (metadataToPersist.get(UUID) == null || metadataToPersist.get(UUID).toString().isBlank()) {
                metadataToPersist.put(UUID, java.util.UUID.randomUUID().toString());
            }
            String metaUuid = metadataToPersist.get(UUID).toString();
            // Salva il metadata
            Map<String, Object> savedMeta = getApiService().createIfNotExists(METADATAS, metadataToPersist, UUID);
            eventCreateUpdatePublisher.fireAsync(new MetadataCreateUpdateEvent(savedMeta));
            // Salva le field definitions
            if (fieldsList != null) {
                for (Map<String, Object> fdMap : fieldsList) {
                    // Genera UUID per la field definition se mancante
                    if (fdMap.get(UUID) == null || fdMap.get(UUID).toString().isBlank()) {
                        fdMap.put(UUID, java.util.UUID.randomUUID().toString());
                    }
                    // Assicura il riferimento al metadata
                    fdMap.put("metadata_uuid", metaUuid);
                    getApiService().createFromMap(FIELD_DEFINITIONS, fdMap);
                }
            }
            saved.add(savedMeta);
        }
        return ok(Map.of("imported", saved)).build();
    }

    private Map<String, Object> filterImportableMetadataColumns(Map<String, Object> metaMap) {
        Map<String, Object> filtered = new HashMap<>();
        if (metaMap == null || metaMap.isEmpty()) {
            return filtered;
        }
        for (Map.Entry<String, Object> entry : metaMap.entrySet()) {
            if (IMPORTABLE_METADATA_COLUMNS.contains(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

}
