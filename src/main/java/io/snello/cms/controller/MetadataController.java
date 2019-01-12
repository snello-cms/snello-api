package io.snello.cms.controller;

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.snello.cms.management.AppConstants;
import io.snello.cms.model.events.MetadataCreateUpdateEvent;
import io.snello.cms.model.events.MetadataDeleteEvent;
import io.snello.cms.service.ApiService;
import io.snello.cms.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import java.util.Map;

import static io.micronaut.http.HttpResponse.ok;
import static io.micronaut.http.HttpResponse.serverError;

@Controller(AppConstants.METADATA_PATH)
public class MetadataController {


    @Inject
    ApiService apiService;

    static String table = AppConstants.METADATAS;


    @Inject
    ApplicationEventPublisher eventPublisher;


    Logger logger = LoggerFactory.getLogger(MetadataController.class);

    @Get()
    public HttpResponse<?> list(HttpRequest<?> request,
                                @Nullable @QueryValue(AppConstants.SORT_PARAM) String sort,
                                @Nullable @QueryValue(AppConstants.LIMIT_PARAM) String limit,
                                @Nullable @QueryValue(AppConstants.START_PARAM) String start) throws Exception {
        if (sort != null)
            logger.info("sort: " + sort);
        if (limit != null)
            logger.info("limit: " + limit);
        if (start != null)
            logger.info("start: " + start);
        Integer l = limit == null ? 10 : Integer.valueOf(limit);
        Integer s = start == null ? 0 : Integer.valueOf(start);
        return ok(apiService.list(table, request.getParameters(), sort, l, s))
                .header(AppConstants.SIZE_HEADER_PARAM, "" + apiService.count(table, request.getParameters()));
    }


    @Get(AppConstants.UUID_PATH_PARAM)
    public HttpResponse<?> fetch(HttpRequest<?> request, @NotNull String uuid) throws Exception {
        return ok(apiService.fetch(table, uuid, AppConstants.UUID));
    }

    @Get(AppConstants.UUID_PATH_PARAM_CREATE)
    public HttpResponse<?> create(@NotNull String uuid) throws Exception {
        apiService.createMetadataTable(uuid);
        return ok(apiService.fetch(table, uuid, AppConstants.UUID));
    }


    @Post()
    public HttpResponse<?> post(@Body String body) throws Exception {
        Map<String, Object> map = JsonUtils.fromJson(body);
        map.put(AppConstants.UUID, java.util.UUID.randomUUID().toString());
        map = apiService.create(table, map, AppConstants.UUID);
        eventPublisher.publishEvent(new MetadataCreateUpdateEvent(map));
        return ok(map);
    }

    @Put(AppConstants.UUID_PATH_PARAM)
    public HttpResponse<?> put(@Body String body, @NotNull String uuid) throws Exception {
        Map<String, Object> map = JsonUtils.fromJson(body);
        map = apiService.merge(table, map, uuid, AppConstants.UUID);
        eventPublisher.publishEvent(new MetadataCreateUpdateEvent(map));
        return ok(map);
    }

    @Delete(AppConstants.UUID_PATH_PARAM)
    public HttpResponse<?> delete(HttpRequest<?> request, @NotNull String uuid) throws Exception {
        boolean result = apiService.delete(table, uuid, AppConstants.UUID);
        if (result) {
            eventPublisher.publishEvent(new MetadataDeleteEvent(uuid));
            return ok();
        }
        return serverError();
    }
}