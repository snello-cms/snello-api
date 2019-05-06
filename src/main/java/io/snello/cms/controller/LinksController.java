package io.snello.cms.controller;

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.snello.cms.model.FieldDefinition;
import io.snello.cms.model.Link;
import io.snello.cms.model.Metadata;
import io.snello.cms.model.events.FieldDefinitionCreateUpdateEvent;
import io.snello.cms.model.events.MetadataCreateUpdateEvent;
import io.snello.cms.service.ApiService;
import io.snello.cms.service.MetadataService;
import io.snello.util.JsonUtils;
import io.snello.util.MetadataUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.micronaut.http.HttpResponse.ok;
import static io.micronaut.http.HttpResponse.serverError;
import static io.snello.cms.management.AppConstants.*;

@Controller(LINKS_PATH)
public class LinksController {

    @Inject
    ApiService apiService;

    @Inject
    MetadataService metadataService;

    @Inject
    ApplicationEventPublisher eventPublisher;

    static String table = LINKS;


    Logger logger = LoggerFactory.getLogger(MetadataController.class);

    @Get()
    public HttpResponse<?> list(HttpRequest<?> request,
                                @Nullable @QueryValue(SORT_PARAM) String sort,
                                @Nullable @QueryValue(LIMIT_PARAM) String limit,
                                @Nullable @QueryValue(START_PARAM) String start) throws Exception {
        if (sort != null)
            logger.info(SORT_DOT_DOT + sort);
        if (limit != null)
            logger.info(LIMIT_DOT_DOT + limit);
        if (start != null)
            logger.info(START_DOT_DOT + start);
        Integer l = limit == null ? 10 : Integer.valueOf(limit);
        Integer s = start == null ? 0 : Integer.valueOf(start);
        return ok(apiService.list(table, request.getParameters(), sort, l, s))
                .header(SIZE_HEADER_PARAM, EMPTY + apiService.count(table, request.getParameters()))
                .header(TOTAL_COUNT_HEADER_PARAM, EMPTY + apiService.count(table, request.getParameters()));
    }


    @Get(UUID_PATH_PARAM)
    public HttpResponse<?> fetch(HttpRequest<?> request, @NotNull String uuid) throws Exception {
        return ok(apiService.fetch(null, table, uuid, NAME));
    }


    @Post()
    public HttpResponse<?> post(@Body String body) throws Exception {
        Map<String, Object> map = JsonUtils.fromJson(body);
        if (map.get(NAME) == null) {
            throw new Exception(MSG_NAME_PARAM_IS_EMPTY);
        }
        if (MetadataUtils.isReserved(map.get(NAME))) {
            throw new Exception(MSG_NAME_PARAM_IS_RESERVED);
        }
        map.put(NAME, map.get(NAME));
        map = apiService.create(table, map, NAME);
        return ok(map);
    }

    @Put(UUID_PATH_PARAM)
    public HttpResponse<?> put(@Body String body, @NotNull String uuid) throws Exception {
        Map<String, Object> map = JsonUtils.fromJson(body);
        if (map.get(NAME) == null) {
            throw new Exception(MSG_NAME_PARAM_IS_EMPTY);
        }
        if (MetadataUtils.isReserved(map.get(NAME))) {
            throw new Exception(MSG_NAME_PARAM_IS_RESERVED);
        }
        map = apiService.merge(table, map, uuid, NAME);
        return ok(map);
    }

    @Delete(UUID_PATH_PARAM)
    public HttpResponse<?> delete(HttpRequest<?> request, @NotNull String uuid) throws Exception {
        boolean result = apiService.delete(table, uuid, NAME);
        if (result) {
            return ok();
        }
        return serverError();
    }

    @Get(UUID_PATH_PARAM_CREATE)
    public HttpResponse<?> create(@NotNull String uuid) throws Exception {
        Map<String, Object> map = apiService.fetch(null, table, uuid, NAME);
        Link link = new Link(map);
        link.created = true;
        apiService.merge(table, map, uuid, NAME);
        Metadata metadataOriginal =metadataService.metadata(link.metadata_name);
        Metadata metadata = new Metadata();
        metadata.table_name= link.name;
        metadata.uuid = java.util.UUID.randomUUID().toString();
        metadata.table_key = UUID;
        metadata.table_key_type = UUID;
        apiService.create(METADATAS, metadata.toMap(), UUID);
        MetadataCreateUpdateEvent metadataCreateUpdateEvent = new MetadataCreateUpdateEvent(metadata);
        eventPublisher.publishEvent(metadataCreateUpdateEvent);

        String[] fields = link.labels.split(COMMA);
        List<FieldDefinition> fieldDefinitions= new ArrayList<>();
        for (String name: fields) {
            FieldDefinition fieldDefinition = new FieldDefinition();
            fieldDefinition.uuid = java.util.UUID.randomUUID().toString();
            fieldDefinition.label=name;
            fieldDefinition.name=name;
            fieldDefinition.type = JOIN;
            fieldDefinition.metadata_name = metadata.table_name;
            fieldDefinition.metadata_uuid = metadata.uuid;
            fieldDefinition.join_table_key = metadataOriginal.table_key;
            fieldDefinition.join_table_name = metadataOriginal.table_name;
            fieldDefinitions.add(fieldDefinition);
            apiService.create(FIELD_DEFINITIONS, fieldDefinition.toMap(), UUID);
            FieldDefinitionCreateUpdateEvent fieldDefinitionCreateUpdateEvent = new FieldDefinitionCreateUpdateEvent(fieldDefinition);
            eventPublisher.publishEvent(fieldDefinitionCreateUpdateEvent);
        }
        metadataService.createTableFromMetadata(metadata.uuid);
        return ok();
    }
}
