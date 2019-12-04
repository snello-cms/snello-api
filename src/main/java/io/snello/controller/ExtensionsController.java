package io.snello.controller;

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.snello.model.events.ExtensionsCreateUpdateEvent;
import io.snello.model.events.ExtensionsDeleteEvent;
import io.snello.service.ApiService;
import io.snello.util.JsonUtils;
import io.snello.util.MetadataUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import java.util.Map;

import static io.micronaut.http.HttpResponse.ok;
import static io.micronaut.http.HttpResponse.serverError;
import static io.snello.management.AppConstants.*;

@Controller(EXTENSIONS_PATH)
public class ExtensionsController {


    @Inject
    ApiService apiService;

    static String table = EXTENSIONS;


    @Inject
    ApplicationEventPublisher eventPublisher;


    Logger logger = LoggerFactory.getLogger(ExtensionsController.class);

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
        return ok(apiService.fetch(null, table, uuid, UUID));
    }


    @Post()
    public HttpResponse<?> post(@Body String body) throws Exception {
        Map<String, Object> map = JsonUtils.fromJson(body);
        if (map.get(NAME) == null) {
            throw new Exception(MSG_EXTENSION_NAME_IS_EMPTY);
        }
        if (MetadataUtils.isReserved(map.get(NAME))) {
            throw new Exception(MSG_EXTENSION_NAME_IS_RESERVED);
        }
        map.put(UUID, java.util.UUID.randomUUID().toString());
        map = apiService.createIfNotExists(table, map, UUID);
        eventPublisher.publishEvent(new ExtensionsCreateUpdateEvent(map));
        return ok(map);
    }

    @Put(UUID_PATH_PARAM)
    public HttpResponse<?> put(@Body String body, @NotNull String uuid) throws Exception {
        Map<String, Object> map = JsonUtils.fromJson(body);
        if (map.get(NAME) == null) {
            throw new Exception(MSG_EXTENSION_NAME_IS_EMPTY);
        }
        if (MetadataUtils.isReserved(map.get(NAME))) {
            throw new Exception(MSG_EXTENSION_NAME_IS_RESERVED);
        }
        map = apiService.mergeIfNotExists(table, map, uuid, UUID);
        eventPublisher.publishEvent(new ExtensionsCreateUpdateEvent(map));
        return ok(map);
    }

    @Delete(UUID_PATH_PARAM)
    public HttpResponse<?> delete(HttpRequest<?> request, @NotNull String uuid) throws Exception {
        apiService.fetch(null, table, uuid, UUID);
        boolean result = apiService.delete(table, uuid, UUID);
        if (result) {
            eventPublisher.publishEvent(new ExtensionsDeleteEvent(uuid));
            return ok();
        }
        return serverError();
    }
}
