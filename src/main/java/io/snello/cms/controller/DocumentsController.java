package io.snello.cms.controller;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.server.types.files.AttachedFile;
import io.snello.cms.management.AppConstants;
import io.snello.cms.util.DocumentUtils;
import io.snello.cms.service.ApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static io.micronaut.http.HttpResponse.ok;
import static io.micronaut.http.HttpResponse.serverError;

@Controller(AppConstants.DOCUMENTS_PATH)
public class DocumentsController {

    Logger logger = LoggerFactory.getLogger(DocumentsController.class);

    @Property(name = AppConstants.SYSTEM_DOCUMENTS_BASE_PATH)
    String basePath;

    @Inject
    ApiService apiService;

    static String documents_table = AppConstants.DOCUMENTS;


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
        return ok(apiService.list(documents_table, request.getParameters(), sort, l, s))
                .header(AppConstants.SIZE_HEADER_PARAM, "" + apiService.count(documents_table, request.getParameters()));
    }

    @Get(AppConstants.UUID_PATH_PARAM)
    public HttpResponse<?> fetch(HttpRequest<?> request, @NotNull String uuid) throws Exception {
        return ok(apiService.fetch(documents_table, uuid, AppConstants.UUID));
    }

    @Get(AppConstants.UUID_PATH_PARAM + AppConstants.DOWNLOAD_PATH)
    public AttachedFile download(@NotNull String uuid) throws Exception {
        Map<String, Object> map = apiService.fetch(documents_table, uuid, AppConstants.UUID);
        File file = null;
        if (map != null) {
            file = Path.of((String) map.get(AppConstants.DOCUMENT_PATH)).toFile();
        }
        return new AttachedFile(file, (String) map.get(AppConstants.DOCUMENT_ORIGINAL_NAME));
    }

    @Post(consumes = MediaType.MULTIPART_FORM_DATA)
    public HttpResponse<?> post(CompletedFileUpload file,
                                @Part(AppConstants.DOCUMENT_TABLE) String table,
                                @Part(AppConstants.DOCUMENT_TABLE_KEY) String table_key) {
        try {
            String uuid = java.util.UUID.randomUUID().toString();
            Map<String, Object> map = DocumentUtils.work(file, uuid, basePath, table, table_key);
            map = apiService.create(documents_table, map, AppConstants.UUID);
            return ok(map);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return HttpResponse.serverError();
    }


    @Put(value = AppConstants.UUID_PATH_PARAM, consumes = MediaType.MULTIPART_FORM_DATA)
    public HttpResponse<?> put(CompletedFileUpload file,
                               @NotNull String uuid,
                               @Part(AppConstants.DOCUMENT_TABLE) String table_name,
                               @Part(AppConstants.DOCUMENT_TABLE) String table_key) {
        try {
            Map<String, Object> map = DocumentUtils.work(file, uuid, basePath, table_name, table_key);
            map = apiService.merge(documents_table, map, uuid, AppConstants.UUID);
            return ok(map);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return HttpResponse.serverError();
    }

    @Delete(AppConstants.UUID_PATH_PARAM)
    public HttpResponse<?> delete(HttpRequest<?> request, @NotNull String uuid, @Nullable @QueryValue(AppConstants.DELETE_PARAM) String delete) throws Exception {
        Map<String, Object> map = apiService.fetch(documents_table, uuid, AppConstants.UUID);
        if (delete != null && delete.toLowerCase().equals("true")) {
            Files.delete(Path.of(
                    basePath,
                    (String) map.get(AppConstants.DOCUMENT_PATH)));
        }
        if (map != null) {
            boolean result = apiService.delete(documents_table, uuid, AppConstants.UUID);
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
