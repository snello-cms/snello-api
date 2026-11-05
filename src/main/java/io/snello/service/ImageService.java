package io.snello.service;


import io.quarkus.logging.Log;
import io.snello.api.service.StorageService;
import io.snello.model.events.ImageEvent;
import io.snello.model.pojo.DocumentFormData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.StreamingOutput;
import net.coobird.thumbnailator.Thumbnails;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;

import static io.snello.management.AppConstants.*;
import static io.snello.util.DocumentUtils.hasFormatToken;

@ApplicationScoped
public class ImageService {

    private static String table = DOCUMENTS;

    @Inject
    StorageService documentsService;

    @Inject
    ApiService apiService;

    public void onEvent(@ObservesAsync ImageEvent imageEvent) {
        Log.info("resize event " + imageEvent);
        if (imageEvent.isNotValid()) {
            return;
        }
        final String uuid = imageEvent.uuid;
        final String format = imageEvent.format;
        if ("webp".equals(format)) {
            webp(uuid);
        } else if (format.startsWith("webp_")) {
            String resizeFormat = format.substring("webp_".length());
            if (resizeFormat.isBlank()) {
                Log.error("Invalid webp format for uuid " + uuid + ": " + format);
                return;
            }
            webp(uuid, resizeFormat);
        } else {
            //resize and upload to s3
            resize(uuid, format);
        }
    }

    @Transactional
    public void webp(final String uuid) {
        //resize and upload to s3
        try {
            Map<String, Object> map = apiService.fetch(null, table, uuid, UUID);
            if (map != null) {
                String path = (String) map.get(DOCUMENT_PATH);
                String mimetype = (String) map.get(DOCUMENT_MIME_TYPE);
                if (path == null || mimetype == null) {
                    Log.error("Missing document path or mime type for uuid " + uuid);
                    return;
                }
                String formats = (String) map.get(FORMATS);
                boolean itemExists = hasFormatToken(formats, "webp");
                if (!itemExists) {
                    String ruuid = uuid + "_webp";
                    BufferedImage originalImg = downloadImageFromS3(path, mimetype);
                    if (originalImg == null) {
                        Log.error("Unable to read image from storage for uuid " + uuid);
                        return;
                    }
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    ImageIO.write(originalImg, "webp", outputStream);
                    uploadImageToS3(ruuid, outputStream, map);
                    if (formats != null && !formats.trim().isEmpty()) {
                        map.put(FORMATS, formats + "," + "webp");
                    } else {
                        map.put(FORMATS, "webp");
                    }
                    apiService.merge(table, map, uuid, UUID);
                }
            }
        } catch (Exception e) {
            Log.error("Failed to create webp in ImageService: " + e);
        }
    }

    @Transactional
    public void webp(final String uuid, final String format) {
        try {
            Map<String, Object> map = apiService.fetch(null, table, uuid, UUID);
            if (map != null) {
                String path = (String) map.get(DOCUMENT_PATH);
                String mimetype = (String) map.get(DOCUMENT_MIME_TYPE);
                if (path == null || mimetype == null) {
                    Log.error("Missing document path or mime type for uuid " + uuid);
                    return;
                }
                String webpFormat = "webp_" + format;
                String formats = (String) map.get(FORMATS);
                boolean itemExists = hasFormatToken(formats, webpFormat);
                if (!itemExists) {
                    String ruuid = uuid + "_" + webpFormat;
                    BufferedImage originalImg = downloadImageFromS3(path, mimetype);
                    if (originalImg == null) {
                        Log.error("Unable to read image from storage for uuid " + uuid);
                        return;
                    }
                    ByteArrayOutputStream outputStream = resizeAsFormat(originalImg, format, "webp");
                    uploadImageToS3(ruuid, outputStream, map);
                    if (formats != null && !formats.trim().isEmpty()) {
                        map.put(FORMATS, formats + "," + webpFormat);
                    } else {
                        map.put(FORMATS, webpFormat);
                    }
                    apiService.merge(table, map, uuid, UUID);
                }
            }
        } catch (Exception e) {
            Log.error("Failed to create formatted webp in ImageService: " + e);
        }
    }

    @Transactional
    public void resize(final String uuid, final String format) {
        //resize and upload to s3
        try {
            Map<String, Object> map = apiService.fetch(null, table, uuid, UUID);
            if (map != null) {
                String path = (String) map.get(DOCUMENT_PATH);
                String mimetype = (String) map.get(DOCUMENT_MIME_TYPE);
                if (path == null || mimetype == null) {
                    Log.error("Missing document path or mime type for uuid " + uuid);
                    return;
                }
                String formats = (String) map.get(FORMATS);
                boolean itemExists = hasFormatToken(formats, format);
                if (!itemExists) {
                    String ruuid = uuid + "_" + format;
                    BufferedImage originalImg = downloadImageFromS3(path, mimetype);
                    if (originalImg == null) {
                        Log.error("Unable to read image from storage for uuid " + uuid);
                        return;
                    }
                    ByteArrayOutputStream resizedBaos = resize(originalImg, format, mimetype);
                    uploadImageToS3(ruuid, resizedBaos, map);
                    if (formats != null && !formats.trim().isEmpty()) {
                        map.put(FORMATS, formats + "," + format);
                    } else {
                        map.put(FORMATS, format);
                    }
                    apiService.merge(table, map, uuid, UUID);
                }
            }
        } catch (Exception e) {
            Log.error("Failed to create resource in ImageService: " + e);
        }
    }

    private ByteArrayOutputStream resize(BufferedImage originalImage, String format, String mime_type) throws Exception {
        String targetImgFormat = getFormatForMimeType(mime_type);
        return resizeAsFormat(originalImage, format, targetImgFormat);
    }

    private ByteArrayOutputStream resizeAsFormat(BufferedImage originalImage, String format, String targetImgFormat) throws Exception {
        String[] tokens = format.split("x");
        int targetWidth = Integer.valueOf(tokens[0]);
        int targetHeight = Integer.valueOf(tokens[1]);
        Log.infov("new width = " + targetWidth + ", new height = " + targetHeight + ", target image format = " + targetImgFormat);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Thumbnails.of(originalImage)
                .size(targetWidth, targetHeight)
                .outputFormat(targetImgFormat)
                .outputQuality(1)
                .toOutputStream(outputStream);
        return outputStream;
    }

    private BufferedImage downloadImageFromS3(String path, String mimetype) throws Exception {
        StreamingOutput output = documentsService.streamingOutput(path, mimetype);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        output.write(baos);
        InputStream is = new ByteArrayInputStream(baos.toByteArray());
        BufferedImage originalImage = ImageIO.read(is);
        int imageWidth = originalImage.getWidth();
        int imageHeight = originalImage.getHeight();
        Log.infov("image width = " + imageWidth + " image height = " + imageHeight);
        return originalImage;
    }

    private void uploadImageToS3(String uuid, ByteArrayOutputStream resizedBaos, Map<String, Object> map) throws Exception {
        // storage uploads from a file, so hand the resized bytes over through a short-lived temp file
        java.nio.file.Path resizedFile = java.nio.file.Files.createTempFile("resized_" + uuid + "_", ".img");
        DocumentFormData documentFormData = new DocumentFormData();
        try {
            try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(resizedFile)) {
                resizedBaos.writeTo(out);
            }
            documentFormData.localFile = resizedFile;
            documentFormData.localFileTemporary = true;
            documentFormData.uuid = uuid;
            documentFormData.mimeType = (String) map.get(DOCUMENT_MIME_TYPE);
            documentFormData.filename = (String) map.get(DOCUMENT_NAME);
            documentFormData.table_name = (String) map.get(TABLE_NAME);
            documentFormData.table_key = (String) map.get(TABLE_KEY);
            documentsService.upload(documentFormData);
        } finally {
            documentFormData.cleanup();
            java.nio.file.Files.deleteIfExists(resizedFile);
        }
    }

    private String getFormatForMimeType(String mime_type) throws Exception {
        if (mime_type == null || mime_type.trim().isEmpty()) {
            throw new Exception("mime type is null or empty");
        }
        String[] formats = ImageIO.getWriterFormatNames();
        String lmime_type = mime_type.toLowerCase();
        for (String f : formats) {
            String lf = f.toLowerCase();
            if (lmime_type.indexOf(lf) > 0 || lf.equals(lmime_type))
                return f;
        }
        throw new Exception(String.format("Failed to find Image type for mime type [%s]", mime_type));
    }

}
