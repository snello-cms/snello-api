package io.snello.service.rs;

import io.quarkus.arc.properties.IfBuildProperty;
import io.snello.management.SnelloPlane;

import io.quarkus.logging.Log;

import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.common.constraint.Nullable;
import io.snello.api.service.StorageService;
import io.snello.management.AppConstants;
import io.snello.model.events.ImageEvent;
import io.snello.model.pojo.DocumentFormData;
import io.snello.service.ApiService;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import io.vertx.ext.web.RoutingContext;

import java.io.File;
import java.util.Map;
import java.util.Optional;

import static io.snello.management.AppConstants.*;
import io.snello.util.ContentDispositionUtils;

import static io.snello.util.ContentDispositionUtils.downloadName;
import static io.snello.util.DocumentUtils.hasFormatToken;
import static io.snello.util.DocumentUtils.resolveRemoteFile;
import static io.snello.util.DocumentUtils.validateDocumentFormData;
import static jakarta.ws.rs.core.Response.ok;
import static jakarta.ws.rs.core.Response.serverError;

@Path(DOCUMENTS_PATH)
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class DocumentServiceRs {
    private static String table = DOCUMENTS;

    @Inject
    StorageService documentsService;

    @Inject
    ApiService apiService;

    @Inject
    Event<ImageEvent> imageEvent;

    public DocumentServiceRs() {
    }

    @GET
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)

    public Response fetch(@PathParam("id") String id) throws Exception {
        return ok(requireDocument(id)).build();
    }

    @GET
    @Path(UUID_PATH_PARAM + DOWNLOAD_PATH)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response download(@PathParam("uuid") @NotNull String uuid, @QueryParam(value = "format") String format)
            throws Exception {
        Log.info("download - " + uuid + "," + format);
        Map<String, Object> map = requireDocument(uuid);
        String path = (String) map.get(DOCUMENT_PATH);
        String mimetype = (String) map.get(DOCUMENT_MIME_TYPE);
        String filename = downloadName(map);
        Log.infof("download - uuid=%s filename=%s", uuid, filename);
        String formats = (String) map.get(FORMATS);
        if (format != null && !format.isBlank()) {
            boolean itemExists = hasFormatToken(formats, format);
            if (itemExists) {
                String duuid = uuid + "_" + format;
                String dpath = path.replace(uuid, duuid);
                StreamingOutput output = documentsService.streamingOutput(dpath, mimetype);
                return Response.ok(output)
                        .header("Content-Disposition", ContentDispositionUtils.of("attachment", filename))
                        .build();
            } else {
                imageEvent.fireAsync(new ImageEvent(uuid, format));
            }
        }
        StreamingOutput output = documentsService.streamingOutput(path, mimetype);
        if (format != null) {
            return Response.ok(output)
                    .header("Content-Disposition", ContentDispositionUtils.of("inline", filename))
                    .build();
        } else {
            return Response.ok(output)
                    .header("Content-Disposition", ContentDispositionUtils.of("attachment", filename))
                    .build();
        }
    }

    @GET
    @Path(UUID_PATH_PARAM + WEBP_PATH)
    @Consumes("*/*")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)

    public Response webp(@PathParam("uuid") @NotNull String uuid,
            @QueryParam(value = "format") String format) throws Exception {
        Log.info("webp - " + uuid + "," + format);
        Map<String, Object> map = requireDocument(uuid);
        String path = (String) map.get(DOCUMENT_PATH);
        String mimetype = (String) map.get(DOCUMENT_MIME_TYPE);
        String filename = (String) map.get(DOCUMENT_NAME);
        String formats = (String) map.get(FORMATS);
        boolean isConvertible = (mimetype != null && (mimetype.toLowerCase().contains("png")
                || mimetype.toLowerCase().contains("jpg") || mimetype.toLowerCase().contains("jpeg"))) ||
                (filename != null && (filename.toLowerCase().contains(".png") || filename.toLowerCase().contains(".jpg")
                        || filename.toLowerCase().contains(".jpeg")));
        String requestedFormat = (format != null && !format.isBlank()) ? "webp_" + format.trim() : "webp";
        boolean itemExists = hasFormatToken(formats, requestedFormat);
        if (itemExists) {
            String duuid = uuid + "_" + requestedFormat;
            String dpath = path.replace(uuid, duuid);
            StreamingOutput output = documentsService.streamingOutput(dpath, mimetype);
            return Response.ok(output)
                    .header("Content-Disposition", "inline; filename=\"" + uuid + ".webp" + "\"")
                    .build();
        } else if (isConvertible) {
            Log.info("webp - isConvertible:" + isConvertible);
            imageEvent.fireAsync(new ImageEvent(uuid, requestedFormat));
        } else {
            Log.info("NO webp - isConvertible:" + isConvertible + ",itemExists:" + itemExists + ", mimetype: "
                    + mimetype + ", filename: " + filename);
        }

        StreamingOutput output = documentsService.streamingOutput(path, mimetype);
        return Response.ok(output)
                .header("Content-Disposition", "inline; filename=\"" + filename + "\"")
                .build();
    }

    @GET
    @Path("/{uuid}/download/{name}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Consumes(MediaType.APPLICATION_JSON)

    public Response downloadWithName(@PathParam("uuid") @NotNull String uuid,
            @PathParam("name") @NotNull String name) throws Exception {
        Map<String, Object> map = requireDocument(uuid);
        String path = (String) map.get(DOCUMENT_PATH);
        String mimetype = (String) map.get(DOCUMENT_MIME_TYPE);
        StreamingOutput output = documentsService.streamingOutput(path, mimetype);
        return Response.ok(output)
                .header("Content-Disposition", "attachment; filename=\"" + name + "\"")
                .build();
    }

    @GET
    @Path("/{uuid}/video/{name}")
    @Produces("video/mp4")
    public Response video(@PathParam("uuid") @NotNull String uuid,
            @PathParam("name") @NotNull String name,
            @Context RoutingContext routingContext) throws Exception {
        Map<String, Object> map = requireDocument(uuid);
        String path = (String) map.get(DOCUMENT_PATH);
        File file = documentsService.getFile(path);
        routingContext.addBodyEndHandler(v -> deleteTempFile(file));
        routingContext.addEndHandler(v -> deleteTempFile(file));
        return Response.ok(file)
                .header("Content-Disposition", "inline; filename=\"" + name + "\"")
                .build();
    }

    private void deleteTempFile(File file) {
        if (file != null && file.exists() && !file.delete()) {
            Log.warn("Unable to delete temp video file: " + file.getAbsolutePath());
        }
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)

    public Response persist(@BeanParam DocumentFormData documentFormData) throws Exception {
        try {
            resolveRemoteFile(documentFormData);
            validateDocumentFormData(documentFormData);
            String uuid = java.util.UUID.randomUUID().toString();
            documentFormData.uuid = uuid;
            Log.infof("document upload: uuid=%s table=%s name=%s", uuid, documentFormData.table_name,
                    documentFormData.resolvedOriginalName());
            Map<String, Object> map = documentsService.upload(documentFormData);
            map = apiService.create(table, map, AppConstants.UUID);
            return ok(map).build();
        } finally {
            documentFormData.cleanup();
        }
    }

    @PUT
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Path("/{uuid}")
    @Produces(MediaType.APPLICATION_JSON)

    public Response update(@BeanParam DocumentFormData documentFormData,
            @PathParam("uuid") @NotNull String uuid) throws Exception {
        // Map<String, Object> map = documentsService.upload(file, uuid, table_name,
        // table_key);
        try {
            resolveRemoteFile(documentFormData);
            validateDocumentFormData(documentFormData);
            documentFormData.uuid = uuid;
            Log.infof("document re-upload: uuid=%s table=%s name=%s", uuid, documentFormData.table_name,
                    documentFormData.resolvedOriginalName());
            Map<String, Object> map = documentsService.upload(documentFormData);
            map = apiService.merge(table, map, uuid, AppConstants.UUID);
            return ok(map).build();
        } finally {
            documentFormData.cleanup();
        }
    }

    @PUT
    @Path("/{id}/data")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateData(@PathParam("id") String id,
            Map<String, Object> map) throws Exception {
        map = apiService.merge(table, map, id, AppConstants.UUID);
        return ok(map).build();
    }

    @Path("/{uuid}")
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)

    public Response delete(@PathParam("uuid") @NotNull String uuid,
            @Nullable @QueryParam("delete") String delete) throws Exception {
        Map<String, Object> map = requireDocument(uuid);
        if (delete != null && delete.toLowerCase().equals(TRUE)) {
            documentsService.delete((String) map.get(DOCUMENT_PATH));
        }
        boolean result = apiService.delete(table, uuid, AppConstants.UUID);
        if (result) {
            return ok().build();
        }
        return serverError().build();
    }

    /** Loads the documents row or answers 404 when uuid is unknown. */
    private Map<String, Object> requireDocument(String uuid) throws Exception {
        Map<String, Object> map = apiService.fetch(null, table, uuid, AppConstants.UUID);
        if (map == null) {
            throw new NotFoundException("document not found: " + uuid);
        }
        return map;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)

    public Response list(
            @QueryParam(SORT_PARAM) String sort,
            @QueryParam(LIMIT_PARAM) String limit,
            @QueryParam(START_PARAM) String start,
            @Context UriInfo uriInfo) throws Exception {
        if (sort != null)
            Log.info(SORT_DOT_DOT + sort);
        else
            sort = "table_name asc";
        if (limit != null)
            Log.info(LIMIT_DOT_DOT + limit);
        if (start != null)
            Log.info(START_DOT_DOT + start);
        int l = Optional.ofNullable(limit).map(Integer::parseInt).orElse(10);
        int s = Optional.ofNullable(start).map(Integer::parseInt).orElse(0);
        return ok(apiService.list(table, uriInfo.getQueryParameters(), sort, l, s))
                .header(SIZE_HEADER_PARAM, EMPTY + apiService.count(table, uriInfo))
                .header(TOTAL_COUNT_HEADER_PARAM, EMPTY + apiService.count(table, uriInfo)).build();
    }

}
