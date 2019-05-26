package io.snello.controller;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.server.types.files.SystemFile;
import io.snello.service.ApiService;
import io.snello.service.documents.DocumentsService;
import io.snello.management.AppConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.nio.file.Path;
import java.util.Map;

import static io.micronaut.http.HttpResponse.ok;
import static io.micronaut.http.HttpResponse.serverError;
import static io.snello.management.AppConstants.*;

@Controller(DOCUMENTS_PATH)
public class DocumentsController {

    Logger logger = LoggerFactory.getLogger(DocumentsController.class);

    @Inject
    ApiService apiService;

     String table = DOCUMENTS;

    @Inject
    DocumentsService documentsService;

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

    @Get(UUID_PATH_PARAM + DOWNLOAD_PATH)
    public SystemFile download(@NotNull String uuid) throws Exception {
        Map<String, Object> map = apiService.fetch(null, table, uuid, AppConstants.UUID);
        File file = null;
        if (map != null) {
            String pathComplete = documentsService.basePath(EMPTY) + BASE_PATH + map.get(DOCUMENT_PATH);
            file = Path.of(pathComplete).toFile();
        }
//        return new AttachedFile(file, (String) map.get(DOCUMENT_ORIGINAL_NAME));
        return new SystemFile(file);
    }

    @Post(consumes = MediaType.MULTIPART_FORM_DATA)
    public HttpResponse<?> post(CompletedFileUpload file,
                                @Part(TABLE_NAME) String table_name,
                                @Part(TABLE_KEY) String table_key) {
        try {
            String uuid = java.util.UUID.randomUUID().toString();
            Map<String, Object> map = documentsService.upload(file, uuid, table_name, table_key);
            map = apiService.create(table, map, AppConstants.UUID);
            return ok(map);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return HttpResponse.serverError();
    }


    @Put(value = UUID_PATH_PARAM, consumes = MediaType.MULTIPART_FORM_DATA)
    public HttpResponse<?> put(CompletedFileUpload file,
                               @NotNull String uuid,
                               @Part(TABLE_NAME) String table_name,
                               @Part(TABLE_KEY) String table_key) {
        try {
            Map<String, Object> map = documentsService.upload(file, uuid, table_name, table_key);
            map = apiService.merge(table, map, uuid, AppConstants.UUID);
            return ok(map);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return HttpResponse.serverError();
    }

    @Delete(UUID_PATH_PARAM)
    public HttpResponse<?> delete(HttpRequest<?> request, @NotNull String uuid, @Nullable @QueryValue(DELETE_PARAM) String delete) throws Exception {
        Map<String, Object> map = apiService.fetch(null, table, uuid, AppConstants.UUID);
        if (delete != null && delete.toLowerCase().equals(TRUE)) {
            documentsService.delete((String) map.get(DOCUMENT_PATH));
        }
        if (map != null) {
            boolean result = apiService.delete(table, uuid, AppConstants.UUID);
            if (result) {
                return ok();
            } else {
                return serverError();
            }
        } else {
            return serverError();
        }
    }
}
