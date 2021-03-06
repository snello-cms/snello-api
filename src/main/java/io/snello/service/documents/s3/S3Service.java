package io.snello.service.documents.s3;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import io.minio.MinioClient;
import io.snello.management.AppConstants;
import io.snello.service.documents.DocumentsService;
import io.snello.util.MimeUtils;
import io.snello.util.ResourceFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.snello.management.AppConstants.*;

@Singleton
@Requires(property = STORAGE_TYPE, value = "s3")
public class S3Service implements DocumentsService {


    Logger logger = LoggerFactory.getLogger(S3Service.class);

    MinioClient minioClient;

    @Property(name = S3_ENDPOINT)
    String s3_endpoint;

    @Property(name = S3_ACCESS_KEY)
    String s3_access_key;

    @Property(name = S3_SECRET_KEY)
    String s3_secret_key;

    @Property(name = S3_REGION)
    String s3_region;

    @Property(name = S3_BUCKET_NAME)
    String s3_bucket_name;

    @Property(name = S3_BUCKET_FOLDER)
    String s3_bucket_folder;


    @EventListener
    public void onStartup(ServerStartupEvent event) {
        logger.info("S3Service load");
        init();
    }

    public void init() {
        try {
            logger.info("s3 s3_endpoint: " + s3_endpoint + ",s3_access_key: " + s3_access_key + ",s3_secret_key: "
                    + s3_secret_key + ",s3_bucket_name: " + s3_bucket_name);
            minioClient = new MinioClient(
                    s3_endpoint, //MINIO_ENDPOINT,
                    s3_access_key,  //MINIO_ACCESS_KEY,
                    s3_secret_key,
                    true); //MINIO_SECRET_KEY);
            verificaBucket(s3_bucket_name);
            verificaFolder();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void verificaBucket(String bucket) throws Exception {
        boolean isExist = minioClient.bucketExists(bucket);
        if (isExist) {
            logger.info("Bucket already exists.");
        } else {
            minioClient.makeBucket(bucket);
        }
    }

    private void verificaFolder() throws Exception {
        logger.info("Bucket verificaFolder: " + s3_bucket_folder);
        if (s3_bucket_folder != null && !s3_bucket_folder.trim().isEmpty()) {
//            if (!s3_bucket_folder.startsWith("/")) {
//                s3_bucket_folder = "/" + s3_bucket_folder;
//            }
            if (!s3_bucket_folder.endsWith("/")) {
                s3_bucket_folder = s3_bucket_folder + "/";
            }
            logger.info("Bucket folder: " + s3_bucket_folder);
        } else {
            s3_bucket_folder = null;
        }
    }


    @Override
    public String basePath(String folder) {
        if (s3_bucket_folder != null && !s3_bucket_folder.trim().isEmpty()) {
            return s3_bucket_folder + folder;
        }
        return folder;
    }

    @Override
    public Map<String, Object> upload(CompletedFileUpload file, String uuid, String table_name, String table_key) throws Exception {
        String extension = file.getContentType().get().getExtension();
        String name = basePath(table_name) + "/" + uuid + "." + extension;
        Map<String, Object> map = new HashMap<>();
        map.put(AppConstants.UUID, uuid);
        map.put(DOCUMENT_NAME, uuid + "." + extension);
        map.put(DOCUMENT_ORIGINAL_NAME, file.getFilename());
        map.put(DOCUMENT_PATH, name);
        map.put(DOCUMENT_MIME_TYPE, file.getContentType().get().getName());
        map.put(SIZE, file.getSize());
        map.put(TABLE_NAME, table_name);
        map.put(TABLE_KEY, table_key);
        minioClient.putObject(s3_bucket_name, name, file.getInputStream(), file.getSize(), file.getContentType().toString());
        logger.info("document uploaded!");
        return map;
    }

    @Override
    public Map<String, Object> write(File file, String uuid, String table_name) throws Exception {
        Map<String, Object> map = new HashMap<>();
        String extension = ResourceFileUtils.getExtension(file.getName());
        String name = basePath(table_name) + "/" + uuid + "." + extension;
        minioClient.putObject(s3_bucket_name, name, file.getAbsolutePath());
        map.put(TABLE_NAME, table_name);
        map.put(DOCUMENT_PATH, name);
        return map;
    }

    @Override
    public Map<String, Object> write(byte[] bytes, String uuid, String table_name, String extension) throws Exception {
        Map<String, Object> map = new HashMap<>();
        String name = basePath(table_name) + "/" + uuid + "." + extension;
        InputStream stream = new ByteArrayInputStream(bytes);
        int size = bytes.length;
        String contentType = MimeUtils.getContentType(name);
        minioClient.putObject(s3_bucket_name, name, stream, size, contentType);
        map.put(TABLE_NAME, table_name);
        map.put(DOCUMENT_PATH, name);
        return map;
    }

    @Override
    public StreamedFile streamingOutput(String path, String mediatype) throws Exception {
        minioClient.statObject(s3_bucket_name, path);
        InputStream input = minioClient.getObject(s3_bucket_name, path);
        return new StreamedFile(input, new MediaType(mediatype));
    }


    @Override
    public boolean delete(String filename) throws Exception {
        minioClient.statObject(s3_bucket_name, filename);
        minioClient.removeObject(s3_bucket_name, filename);
        return true;
    }

    @Override
    public File getFile(String path) throws Exception {
        String ext = ResourceFileUtils.getExtension(path);
        File temp = File.createTempFile(java.util.UUID.randomUUID().toString(), ext);
        InputStream inputStream = minioClient.getObject(s3_bucket_name, path);
        Files.copy(inputStream, temp.toPath());
        return temp;
    }


}
