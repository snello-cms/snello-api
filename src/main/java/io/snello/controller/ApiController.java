package io.snello.controller;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.snello.model.FieldDefinition;
import io.snello.model.Metadata;
import io.snello.service.ApiService;
import io.snello.util.SqlDetectUtils;
import io.snello.util.TableKeyUtils;
import io.snello.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import java.util.*;

import static io.micronaut.http.HttpResponse.ok;
import static io.micronaut.http.HttpResponse.serverError;
import static io.snello.management.AppConstants.*;

@Controller(API_PATH)
public class ApiController {

    Logger logger = LoggerFactory.getLogger(ApiController.class);

    @Inject
    ApiService apiService;

    public ApiController() {
    }


    @Get(TABLE_PATH_PARAM)
    public HttpResponse<?> list(HttpRequest<?> request,
                                @NotNull String table,
                                @Nullable @QueryValue(SORT_PARAM) String sort,
                                @Nullable @QueryValue(LIMIT_PARAM) String limit,
                                @Nullable @QueryValue(START_PARAM) String start) throws Exception {
        sort = SqlDetectUtils.detectSqlSort(sort);
        if (sort != null)
            logger.info(SORT_DOT_DOT + sort);
        if (limit != null)
            logger.info(LIMIT_DOT_DOT + limit);
        if (start != null)
            logger.info(START_DOT_DOT + start);
        debug(request, null);
        Integer l = limit == null ? 10 : Integer.valueOf(limit);
        Integer s = start == null ? 0 : Integer.valueOf(start);
        long count = apiService.count(table, request.getParameters());
        return ok(apiService.list(table, request.getParameters(), sort, l, s))
                .header(SIZE_HEADER_PARAM, "" + count)
                .header(TOTAL_COUNT_HEADER_PARAM, "" + count);
    }


    @Get(TABLE_PATH_PARAM + UUID_PATH_PARAM)
    public HttpResponse<?> fetch(HttpRequest<?> request, @NotNull String table, @NotNull String uuid) throws Exception {
        debug(request, null);
        String key = apiService.table_key(table);
        return ok(apiService.fetch(request.getParameters(), table, uuid, key));
    }


    @Get(TABLE_PATH_PARAM + UUID_PATH_PARAM + EXTRA_PATH_PARAM)
    public HttpResponse<?> get(HttpRequest<?> request, @NotNull String table, @NotNull String uuid, @NotNull String path,
                               @Nullable @QueryValue(SORT_PARAM) String sort,
                               @Nullable @QueryValue(LIMIT_PARAM) String limit,
                               @Nullable @QueryValue(START_PARAM) String start) throws Exception {
        sort = SqlDetectUtils.detectSqlSort(sort);
        debug(request, path);
        if (path == null) {
            throw new Exception(MSG_PATH_IS_EMPTY);
        }
        if (start == null) {
            start = _0;
        }
        if (limit == null) {
            limit = _10;
        }
        logger.info("path accessorio: " + path);
        if (path.contains("/")) {
            String[] pars = path.split(BASE_PATH);
            if (pars.length > 1) {
                return ok(apiService.fetch(request.getParameters(), pars[0], pars[1], UUID));
            } else {
                return ok(apiService.list(pars[0], request.getParameters(), sort, Integer.valueOf(limit), Integer.valueOf(start)));
            }
        } else {
            Map<String, List<String>> parametersMap = null;
            if (request.getParameters() != null) {
                parametersMap = request.getParameters().asMap();
            } else {
                parametersMap = new HashMap<>();
            }
            parametersMap.put(table + "_id", Arrays.asList(new String[]{uuid}));
            parametersMap.put("join_table", Arrays.asList(new String[]{table + "_" + path}));
            return ok(apiService.list(path, parametersMap, sort, Integer.valueOf(limit), Integer.valueOf(start)));
        }
    }


    @Post(TABLE_PATH_PARAM)
    public HttpResponse<?> post(@Body String body, @NotNull String table) throws Exception {
        Map<String, Object> map = JsonUtils.fromJson(body);
        Metadata metadata = apiService.metadataWithFields(table);
        String key = metadata.table_key;
        TableKeyUtils.generateUUid(map, metadata, apiService);
        // CI VUOLE UNA TRANSAZIONE PER TENERE TUTTO INSIEME
        for (FieldDefinition fd : metadata.fields) {
            if ("multijoin".equals(fd.type)) {
                if (map.containsKey(fd.name) && map.get(fd.name) != null) {
                    String join_table_uuids_value = (String) map.get(fd.name);
                    String[] join_table_uuids = join_table_uuids_value.split(",|;");
                    for (String ss : join_table_uuids) {
                        String join_table_name = metadata.table_name + "_" + fd.join_table_name;
                        String table_id = metadata.table_name + "_id";
                        String join_table_id = fd.join_table_name + "_id";
                        Map<String, Object> join_map = new HashMap<>();
                        join_map.put(table_id, map.get(metadata.table_key));
                        join_map.put(join_table_id, ss.trim());
                        apiService.createFromMap(join_table_name, join_map);
                    }
                    //ELIMINO I VALORI NEL CAMPO DI APPOGGIO
                    map.remove(fd.name);
                }
            }
        }
        map = apiService.create(table, map, key);
        return ok(map);
    }

    @Put(TABLE_PATH_PARAM + UUID_PATH_PARAM)
    public HttpResponse<?> put(@Body String body, @NotNull String table, @NotNull String uuid) throws Exception {
        Map<String, Object> map = JsonUtils.fromJson(body);
        boolean renewSlug = TableKeyUtils.isSlug(apiService.metadata(table));
        String key = apiService.table_key(table);
        if (renewSlug) {
            String fieldSluggable = apiService.slugField(table);
            String toSlugValue = (String) map.get(fieldSluggable);
            String slugged = TableKeyUtils.createSlug(toSlugValue);
            logger.info("toSlugValue: " + toSlugValue + ", old slug: " + uuid);
            if (!uuid.equals(slugged)) {
                logger.info("renew slug");
                TableKeyUtils.generateUUid(map, apiService.metadata(table), apiService);
            } else {
                logger.info(" slug is the same!!");
            }
        }
        // CI VUOLE UNA TRANSAZIONE PER TENERE TUTTO INSIEME
        map = apiService.merge(table, map, uuid, key);
        //DEVO ELIMINARE TUTTI I VALORI
        Metadata metadata = apiService.metadataWithFields(table);
        for (FieldDefinition fd : metadata.fields) {
            if ("multijoin".equals(fd.type)) {
                String join_table_name = metadata.table_name + "_" + fd.join_table_name;
                String table_id = metadata.table_name + "_id";
                apiService.delete(join_table_name, table_id, uuid);
                if (map.containsKey(fd.name) && map.get(fd.name) != null) {
                    String join_table_uuids_value = (String) map.get(fd.name);
                    String[] join_table_uuids = join_table_uuids_value.split(",|;");
                    for (String ss : join_table_uuids) {
                        String join_table_id = fd.join_table_name + "_id";
                        Map<String, Object> join_map = new HashMap<>();
                        join_map.put(table_id, map.get(metadata.table_key));
                        join_map.put(join_table_id, ss.trim());
                        apiService.createFromMap(join_table_name, join_map);
                    }
                }
                //ELIMINO I VALORI NEL CAMPO DI APPOGGIO
                map.remove(fd.name);
            }
        }
        return ok(map);
    }

    @Delete(TABLE_PATH_PARAM + UUID_PATH_PARAM)
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


//    @Error(global = true)
//    public HttpResponse<JsonError> error(HttpRequest request, Throwable e) {
//        JsonError error = new JsonError("Errore: " + e.getMessage())
//                .link(Link.SELF, Link.of(request.getUri()));
//
//        return HttpResponse.<JsonError>status(HttpStatus.BAD_REQUEST, e.getMessage())
//                .body(error);
//    }

}
