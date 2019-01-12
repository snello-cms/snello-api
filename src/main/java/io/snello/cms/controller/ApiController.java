package io.snello.cms.controller;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.snello.cms.management.AppConstants;
import io.snello.cms.util.JsonUtils;
import io.snello.cms.util.TableKeyUtils;
import io.snello.cms.service.ApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import java.util.Map;

import static io.micronaut.http.HttpResponse.ok;
import static io.micronaut.http.HttpResponse.serverError;

@Controller(AppConstants.API_PATH)
public class ApiController {

    Logger logger = LoggerFactory.getLogger(ApiController.class);

    @Inject
    ApiService apiService;

    public ApiController() {
    }


    @Get(AppConstants.TABLE_PATH_PARAM)
    public HttpResponse<?> list(HttpRequest<?> request,
                                @NotNull String table,
                                @Nullable @QueryValue(AppConstants.SORT_PARAM) String sort,
                                @Nullable @QueryValue(AppConstants.LIMIT_PARAM) String limit,
                                @Nullable @QueryValue(AppConstants.START_PARAM) String start
    ) throws Exception {
        if (sort != null)
            logger.info("sort: " + sort);
        if (limit != null)
            logger.info("limit: " + limit);
        if (start != null)
            logger.info("start: " + start);
        debug(request, null);
        Integer l = limit == null ? 10 : Integer.valueOf(limit);
        Integer s = start == null ? 0 : Integer.valueOf(start);
        return ok(apiService.list(table, request.getParameters(), sort, l, s))
                .header(AppConstants.SIZE_HEADER_PARAM, "" + apiService.count(table, request.getParameters()));
    }


    @Get(AppConstants.TABLE_PATH_PARAM + AppConstants.UUID_PATH_PARAM)
    public HttpResponse<?> fetch(HttpRequest<?> request, @NotNull String table, @NotNull String uuid) throws Exception {
        debug(request, null);
        String key = apiService.table_key(table);
        return ok(apiService.fetch(table, uuid, key));
    }


    @Get(AppConstants.TABLE_PATH_PARAM + AppConstants.UUID_PATH_PARAM + AppConstants.EXTRA_PATH_PARAM)
    public HttpResponse<?> get(HttpRequest<?> request, @NotNull String table, @NotNull String uuid, @NotNull String path,
                               @Nullable @QueryValue(AppConstants.SORT_PARAM) String sort,
                               @Nullable @QueryValue(AppConstants.LIMIT_PARAM) String limit,
                               @Nullable @QueryValue(AppConstants.START_PARAM) String start) throws Exception {
        debug(request, path);
        if (path == null) {
            throw new Exception("errore non c'e niente");
        }
        if (start == null) {
            start = AppConstants._0;
        }
        if (limit == null) {
            limit = AppConstants._10;
        }
        logger.info("path accessorio: " + path);
        if (path.contains("/")) {
            String[] pars = path.split("/");
            if (pars.length > 1) {
                return ok(apiService.fetch(pars[0], pars[1], AppConstants.UUID));
            } else {
                return ok(apiService.list(pars[0], request.getParameters(), sort, Integer.valueOf(limit), Integer.valueOf(start)));
            }
        } else {
            return ok(apiService.list(path, request.getParameters(), sort, Integer.valueOf(limit), Integer.valueOf(start)));
        }
    }

    @Post(AppConstants.TABLE_PATH_PARAM)
    public HttpResponse<?> post(@Body String body, @NotNull String table) throws Exception {
        Map<String, Object> map = JsonUtils.fromJson(body);
        String key = apiService.table_key(table);
        TableKeyUtils.generateUUid(map, apiService.metadata(table), apiService);
        map = apiService.create(table, map, key);
        return ok(map);
    }

    @Put(AppConstants.TABLE_PATH_PARAM + AppConstants.UUID_PATH_PARAM)
    public HttpResponse<?> put(@Body String body, @NotNull String table, @NotNull String uuid) throws Exception {
        Map<String, Object> map = JsonUtils.fromJson(body);
        String key = apiService.table_key(table);
        map = apiService.merge(table, map, uuid, key);
        return ok(map);
    }

    @Delete(AppConstants.TABLE_PATH_PARAM + AppConstants.UUID_PATH_PARAM)
    public HttpResponse<?> delete(HttpRequest<?> request, @NotNull String table, @NotNull String uuid) throws Exception {
        debug(request, null);
        String key = apiService.table_key(table);
        boolean result = apiService.delete(table, uuid, key);
        if (result)
            return ok();
        return serverError();
    }


    private void debug(HttpRequest<?> request, String path) {
        logger.info("------------");
        logger.info("METHOD: " + request.getMethod().name());
        logger.info("RELATIVE PATH: " + path);
        StringBuffer sb = new StringBuffer();
        request.getParameters().forEach(param -> sb.append(param.getKey() + ":" + param.getValue()));
        if (sb.length() > 0) {
            logger.info("QUERY: " + path);
        }
        logger.info("------------");
        logger.info(request.getPath());
        logger.info("------------");
        request.getParameters().forEach(param -> System.out.print("," + param.getKey() + ":" + param.getValue()));
        logger.info("------------");
    }

}