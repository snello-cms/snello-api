package io.snello.controller;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.snello.management.AppConstants;
import io.snello.service.ApiService;
import io.snello.util.JsonUtils;
import io.snello.util.PasswordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static io.micronaut.http.HttpResponse.ok;
import static io.micronaut.http.HttpResponse.serverError;
import static io.snello.management.AppConstants.CONFIRM_PASSWORD;

@Controller(AppConstants.USERS_PATH)
public class UsersController {

    @Inject
    ApiService apiService;

    String table = AppConstants.USERS;
    String UUID = AppConstants.USERNAME;

    Logger logger = LoggerFactory.getLogger(UsersController.class);


    @Get()
    public HttpResponse<?> list(HttpRequest<?> request,
                                @Nullable @QueryValue(AppConstants.SORT_PARAM) String sort,
                                @Nullable @QueryValue(AppConstants.LIMIT_PARAM) String limit,
                                @Nullable @QueryValue(AppConstants.START_PARAM) String start) throws Exception {
        if (sort != null)
            logger.info(AppConstants.SORT_DOT_DOT + sort);
        if (limit != null)
            logger.info(AppConstants.LIMIT_DOT_DOT + limit);
        if (start != null)
            logger.info(AppConstants.START_DOT_DOT + start);
        Integer l = limit == null ? 10 : Integer.valueOf(limit);
        Integer s = start == null ? 0 : Integer.valueOf(start);
        List<Map<String, Object>> list = apiService.list(table, request.getParameters(), sort, l, s);
        for (Map<String, Object> uu : list) {
            uu.put(AppConstants.PASSWORD, "");
        }
        return ok(list)
                .header(AppConstants.SIZE_HEADER_PARAM, "" + apiService.count(table, request.getParameters()))
                .header(AppConstants.TOTAL_COUNT_HEADER_PARAM, "" + apiService.count(table, request.getParameters()));
    }


    @Get(AppConstants.UUID_PATH_PARAM)
    public HttpResponse<?> fetch(HttpRequest<?> request, @NotNull String uuid) throws Exception {
        Map<String, Object> user = apiService.fetch(null, table, uuid, UUID);
        user.put(AppConstants.PASSWORD, "");
        return ok(user);
    }


    @Post()
    public HttpResponse<?> post(@Body String body) throws Exception {
        Map<String, Object> map = JsonUtils.fromJson(body);
        map.put(UUID, map.get(AppConstants.USERNAME));
        String pwd = PasswordUtils.createPassword((String) map.get(AppConstants.PASSWORD));
        map.put(AppConstants.PASSWORD, pwd);
        map.put(AppConstants.CREATION_DATE, Instant.now().toString());
        map.put(AppConstants.LAST_UPDATE_DATE, Instant.now().toString());
        map = apiService.create(table, map, UUID);
        map.put(AppConstants.PASSWORD, "");
        return ok(map);
    }

    @Put(AppConstants.UUID_PATH_PARAM)
    public HttpResponse<?> put(@Body String body, @NotNull String uuid) throws Exception {
        Map<String, Object> map = JsonUtils.fromJson(body);
        String pwd = (String) map.get(AppConstants.PASSWORD);
        String confirmPwd = (String) map.get(CONFIRM_PASSWORD);
        if (pwd != null && !pwd.trim().isEmpty()) {
            if (confirmPwd != null && !pwd.trim().isEmpty() &&
                    confirmPwd.equals(pwd)) {
                pwd = PasswordUtils.createPassword((String) map.get(AppConstants.PASSWORD));
                map.put(AppConstants.PASSWORD, pwd);
            } else {
                logger.info("ERROR: password != confirmPwd");
                serverError();
            }
        } else {
            logger.info("no change password");
        }
        map.put(AppConstants.LAST_UPDATE_DATE, Instant.now().toString());
        map = apiService.merge(table, map, uuid, UUID);
        map.put(AppConstants.PASSWORD, "");
        return ok(map);
    }

    @Delete(AppConstants.UUID_PATH_PARAM)
    public HttpResponse<?> delete(HttpRequest<?> request, @NotNull String uuid) throws Exception {
        logger.info("QUI NON DOVREI SOLO PASSIVARE??");
        logger.info("QUI NON DOVREI SOLO PASSIVARE??");
        logger.info("QUI NON DOVREI SOLO PASSIVARE??");
        logger.info("QUI NON DOVREI SOLO PASSIVARE??");
        boolean result = apiService.delete(table, uuid, UUID);
        if (result) {
            return ok();
        }
        return serverError();
    }


}
