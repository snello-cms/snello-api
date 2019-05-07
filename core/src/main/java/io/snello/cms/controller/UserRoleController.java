package io.snello.cms.controller;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.snello.cms.management.AppConstants;
import io.snello.cms.ApiService;
import io.snello.util.JsonUtils;
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

@Controller(AppConstants.USER_ROLES_PATH)
public class UserRoleController {

    @Inject
    ApiService apiService;

    static String table = AppConstants.USER_ROLES;
    String UUID = AppConstants.USERNAME;



    Logger logger = LoggerFactory.getLogger(MetadataController.class);

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
        return ok(apiService.list(table, request.getParameters(), sort, l, s))
                .header(AppConstants.SIZE_HEADER_PARAM, "" + apiService.count(table, request.getParameters()))
                .header(AppConstants.TOTAL_COUNT_HEADER_PARAM, "" + apiService.count(table, request.getParameters()));
    }


    @Get(AppConstants.UUID_PATH_PARAM)
    public HttpResponse<?> fetch(HttpRequest<?> request, @NotNull String uuid) throws Exception {
        return ok(apiService.fetch(null, table, uuid, UUID));
    }


    @Post()
    public HttpResponse<?> post(@Body String body) throws Exception {
        Map<String, Object> map = JsonUtils.fromJson(body);
        if (map.get(AppConstants.USERNAME) == null) {
            throw new Exception(AppConstants.MSG_USERNAME_IS_EMPTY);
        }
        String roleString = (String) map.get(AppConstants.ROLE);
        String[] roles = roleString.split(AppConstants.COMMA);
        for (String role : roles) {
            List<Object> userRole = new ArrayList<>();
            userRole.add(map.get(AppConstants.USERNAME));
            userRole.add(role);
            apiService.createUserRole(userRole);
        }

        return ok(map);
    }

    @Put(AppConstants.UUID_PATH_PARAM)
    public HttpResponse<?> put(@Body String body, @NotNull String uuid) throws Exception {
        return HttpResponse.serverError(AppConstants.MSG_NOT_IMPLEMENTED);
    }

    @Delete(AppConstants.UUID_PATH_PARAM)
    public HttpResponse<?> delete(HttpRequest<?> request, @NotNull String uuid) throws Exception {
        boolean result = apiService.delete(table, uuid, UUID);
        if (result) {
            return ok();
        }
        return serverError();
    }
}
