package io.snello.controller;

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.snello.management.AppConstants;
import io.snello.model.events.DraggableCreateUpdateEvent;
import io.snello.model.events.DraggableDeleteEvent;
import io.snello.model.events.DroppableCreateUpdateEvent;
import io.snello.model.events.DroppableDeleteEvent;
import io.snello.service.ApiService;
import io.snello.util.JsonUtils;
import io.snello.util.SqlDetectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import java.util.Map;

import static io.micronaut.http.HttpResponse.ok;
import static io.micronaut.http.HttpResponse.serverError;

@Controller(AppConstants.DROPPABLES_PATH)
public class DroppablesController {

    @Inject
    ApiService apiService;

    String table = AppConstants.DROPPABLES;
    String UUID = AppConstants.UUID;

    Logger logger = LoggerFactory.getLogger(DroppablesController.class);


    @Inject
    ApplicationEventPublisher eventPublisher;


    @Get()
    public HttpResponse<?> list(HttpRequest<?> request,
                                @Nullable @QueryValue(AppConstants.SORT_PARAM) String sort,
                                @Nullable @QueryValue(AppConstants.LIMIT_PARAM) String limit,
                                @Nullable @QueryValue(AppConstants.START_PARAM) String start) throws Exception {
        sort = SqlDetectUtils.detectSqlSort(sort);
        if (sort != null)
            logger.info(AppConstants.SORT_DOT_DOT + sort);
        if (limit != null)
            logger.info(AppConstants.LIMIT_DOT_DOT + limit);
        if (start != null)
            logger.info(AppConstants.START_DOT_DOT + start);
        Integer l = limit == null ? 10 : Integer.valueOf(limit);
        Integer s = start == null ? 0 : Integer.valueOf(start);
        return ok(apiService.list(table, request.getParameters(), sort, l, s))
                .header(AppConstants.SIZE_HEADER_PARAM, AppConstants.EMPTY + apiService.count(table, request.getParameters()))
                .header(AppConstants.TOTAL_COUNT_HEADER_PARAM, AppConstants.EMPTY + apiService.count(table, request.getParameters()));
    }


    @Get(AppConstants.UUID_PATH_PARAM)
    public HttpResponse<?> fetch(HttpRequest<?> request, @NotNull String uuid) throws Exception {
        return ok(apiService.fetch(null, table, uuid, UUID));
    }


    @Post()
    public HttpResponse<?> post(@Body String body) throws Exception {
        Map<String, Object> map = JsonUtils.fromJson(body);
        map.put(UUID, map.get(AppConstants.NAME));
        map = apiService.create(table, map, UUID);
        eventPublisher.publishEvent(new DroppableCreateUpdateEvent(map));
        return ok(map);
    }

    @Put(AppConstants.UUID_PATH_PARAM)
    public HttpResponse<?> put(@Body String body, @NotNull String uuid) throws Exception {
        Map<String, Object> map = JsonUtils.fromJson(body);
        map = apiService.merge(table, map, uuid, UUID);
        eventPublisher.publishEvent(new DroppableCreateUpdateEvent(map));
        return ok(map);
    }

    @Delete(AppConstants.UUID_PATH_PARAM)
    public HttpResponse<?> delete(HttpRequest<?> request, @NotNull String uuid) throws Exception {
        boolean result = apiService.delete(table, uuid, UUID);
        if (result) {
            eventPublisher.publishEvent(new DroppableDeleteEvent(uuid));
            return ok();
        }
        return serverError();
    }
}
