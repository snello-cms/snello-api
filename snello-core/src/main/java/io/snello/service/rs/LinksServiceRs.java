package io.snello.service.rs;



import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.api.service.AbstractServiceRs;
import io.snello.model.FieldDefinition;
import io.snello.model.Link;
import io.snello.model.Metadata;
import io.snello.model.events.MetadataCreateUpdateEvent;
import io.snello.service.ApiService;
import io.snello.service.MetadataService;
import io.snello.util.MetadataUtils;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.snello.management.AppConstants.*;
import static jakarta.ws.rs.core.Response.ok;

@Path(LINKS_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
public class LinksServiceRs extends AbstractServiceRs {

    @Inject
    MetadataService metadataService;

    @Inject
    Event<MetadataCreateUpdateEvent> eventCreateUpdatePublisher;

    @Inject
    public LinksServiceRs(ApiService apiService) {
        super(apiService, LINKS, "");
    }

    public LinksServiceRs() {
        super();
    }

    @Override
    @POST
    
    public Response persist(Map<String, Object> map) throws Exception {
        map.put(NAME, map.get(NAME));
        map = apiService.create(LINKS, map, NAME);
        return ok(map).build();
    }

    @Override
    @PUT
    @Path("/{id}")
    
    public Response update(@PathParam("id") String id, Map<String, Object> map) throws Exception {
        if (map.get(NAME) == null) {
            throw new Exception(MSG_NAME_PARAM_IS_EMPTY);
        }
        if (MetadataUtils.isReserved(map.get(NAME))) {
            throw new Exception(MSG_NAME_PARAM_IS_RESERVED);
        }
        map = apiService.merge(LINKS, map, id, NAME);
        return ok(map).build();
    }

    @GET
    @Path(UUID_PATH_PARAM_CREATE)
    
    public Response create(@NotNull String uuid) throws Exception {
        Map<String, Object> map = apiService.fetch(null, LINKS, uuid, NAME);
        map.put(CREATED, true);
        apiService.merge(LINKS, map, uuid, NAME);
        Link link = new Link(map);
        Metadata metadataOriginal = metadataService.metadata(link.metadata_name);
        Metadata metadata = new Metadata();
        metadata.table_name = link.name;
        metadata.uuid = java.util.UUID.randomUUID().toString();
        metadata.table_key = UUID;
        metadata.table_key_type = UUID;
        metadata.created = true;
        apiService.create(METADATAS, metadata.toMap(), UUID);

        String[] fields = link.labels.split(COMMA_MIXED);
        List<FieldDefinition> fieldDefinitions = new ArrayList<>();
        for (String name : fields) {
            FieldDefinition fieldDefinition = new FieldDefinition();
            fieldDefinition.uuid = java.util.UUID.randomUUID().toString();
            fieldDefinition.label = name;
            fieldDefinition.searchable = true;
            fieldDefinition.search_condition = "contains";
            fieldDefinition.search_field_name = name + "_contains";
            fieldDefinition.show_in_list = true;
            fieldDefinition.name = name;
            fieldDefinition.type = JOIN;
            fieldDefinition.metadata_name = metadata.table_name;
            fieldDefinition.metadata_uuid = metadata.uuid;
            fieldDefinition.join_table_key = metadataOriginal.table_key;
            fieldDefinition.join_table_name = metadataOriginal.table_name;
            fieldDefinition.join_table_select_fields = link.metadata_searchable_field;
            fieldDefinitions.add(fieldDefinition);
            apiService.create(FIELD_DEFINITIONS, fieldDefinition.toMap(), UUID);
        }
        metadataService.createTableFromMetadataAndFields(metadata, fieldDefinitions);
        MetadataCreateUpdateEvent metadataCreateUpdateEvent = new MetadataCreateUpdateEvent(metadata);
        eventCreateUpdatePublisher.fireAsync(metadataCreateUpdateEvent);
        return ok().build();
    }
}
