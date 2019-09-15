package io.snello.controller;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.snello.management.AppConstants;
import io.snello.service.ApiService;
import io.snello.service.documents.DocumentsService;
import io.snello.util.ImageUtils;
import io.snello.util.ResourceFileUtils;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.util.Map;

import static io.micronaut.http.HttpResponse.ok;
import static io.snello.management.AppConstants.*;

@Controller(IMAGES_PATH)
public class ImagesController {

    String table = DOCUMENTS;


    @Inject
    ApiService apiService;


    @Inject
    DocumentsService documentsService;

    @Get(UUID_PATH_PARAM_CROP)
    //    x - the X coordinate of the upper-left corner of the specified rectangular region
    //    y - the Y coordinate of the upper-left corner of the specified rectangular region
    //    width - the width of the specified rectangular region
    //    height - the height of the specified rectangular region
    // /images/{uuid}/crop?w=200&h=150&x=0&y=10 => uuid:string [to create a new image with size, using original ratio ]
    public HttpResponse<?> crop(HttpRequest<?> request, @NotNull String uuid, @NotNull String x, @NotNull String y, @NotNull String w, @NotNull String h) throws Exception {
        Map<String, Object> map = apiService.fetch(null, table, uuid, AppConstants.UUID);
        String path = (String) map.get(DOCUMENT_PATH);
        String table_name = (String) map.get(TABLE_NAME);
        String original_name = (String) map.get(DOCUMENT_ORIGINAL_NAME);
        String extension = ResourceFileUtils.getExtension(path);
        File file = documentsService.getFile(path);
        int xx = Integer.valueOf(x).intValue();
        int yy = Integer.valueOf(y).intValue();
        int ww = Integer.valueOf(w).intValue();
        int hh = Integer.valueOf(h).intValue();
        byte[] bytes = ImageUtils.cropImage(file, extension.toUpperCase(), xx, yy, ww, hh);
        Map<String, Object> resultMap = documentsService.write(bytes, java.util.UUID.randomUUID().toString(), table_name, extension);
        return ok(resultMap);
    }

    @Get(UUID_PATH_PARAM_MOGRIFY)
    // /images/{uuid}/mogrify?w=200&h=150 => uuid:string [to create a new image with size, using original ratio ]
    // /images/{uuid}/mogrify?w=200&h=150&force=true => uuid:string [to create a new image with size ignoring ratio ]
    // /images/{uuid}/mogrify?w=200 => uuid:string [to create a new image with size ignoring ratio ]
    // /images/{uuid}/mogrify?h=150 => uuid:string [to create a new image with size ignoring ratio ]
    public HttpResponse<?> mogrify(HttpRequest<?> request,
                                   @NotNull String uuid,
                                   String w, String h, boolean force) throws Exception {
        Map<String, Object> map = apiService.fetch(null, table, uuid, AppConstants.UUID);
        String path = (String) map.get(DOCUMENT_PATH);
        String table_name = (String) map.get(TABLE_NAME);
        String original_name = (String) map.get(DOCUMENT_ORIGINAL_NAME);
        String extension = ResourceFileUtils.getExtension(path);
        File file = documentsService.getFile(path);

        return ok("TODO");
    }

    @Get(UUID_PATH_PARAM_RESIZE)
    // /images/{uuid}/resize?w=200&h=150 => boolean [to resize a image with new size, using original ratio ]
    // /images/{uuid}/resize?w=200&h=150&force=true => boolean [to resize a image with new size, ignoring ratio ]
    // /images/{uuid}/resize?w=200 => uuid:string boolean [to resize a image with new size - weight proportionally]
    // /images/{uuid}/resize?h=150 => uuid:string boolean [to resize a image with new size - height proportionally ]
    public HttpResponse<?> resize(HttpRequest<?> request, @NotNull String uuid,
                                  @NotNull String w, @NotNull String h, Boolean force) throws Exception {
        File file = getFile(uuid);
        String extension = ResourceFileUtils.getExtension(file.getName());
        if (force != null && force) {
            byte[] bytes = ImageUtils.resizeImage(file, Integer.valueOf(w).intValue(),
                    Integer.valueOf(h).intValue(),
                    extension);
        }
        return ok("TODO");
    }


    private File getFile(String uuid) throws Exception {
        Map<String, Object> map = apiService.fetch(null, table, uuid, AppConstants.UUID);
        if (map == null || map.get(DOCUMENT_PATH) == null) {
            throw new Exception("uuid not valid");
        }
        String path = (String) map.get(DOCUMENT_PATH);
        if (path.isBlank()) {
            throw new Exception("uuid not valid");
        }
        return documentsService.getFile(path);
    }


}
