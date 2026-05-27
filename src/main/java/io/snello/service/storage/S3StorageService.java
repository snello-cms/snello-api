package io.snello.service.storage;

import io.minio.*;
import io.quarkus.logging.Log;
import io.snello.api.service.StorageService;
import io.snello.management.AppConstants;
import io.snello.model.pojo.DocumentFormData;
import io.snello.util.MimeUtils;
import io.snello.util.ResourceFileUtils;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import static io.snello.management.AppConstants.*;

public class S3StorageService implements StorageService {

    private static final int BUFFER_SIZE = 1024;
    private static final long PART_SIZE = 50 * 1024 * 1024;

    MinioClient minioClient;
    String bucketName;
    String base_folder;
    

    public S3StorageService(MinioClient minioClient, String bucketName, String base_folder) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
        this.base_folder = base_folder;
        bucketExists();
        folderExists();
    }


    @Override
    public String basePath(String folder) {
        if (base_folder != null && !base_folder.trim().isEmpty()) {
            return base_folder + folder;
        }
        return folder;
    }

    @Override
    public Map<String, Object> upload(DocumentFormData documentFormData) throws Exception {
        try {
            String originalName = documentFormData.resolvedOriginalName();
            String extension = ResourceFileUtils.getExtension(originalName);
            if (extension == null || extension.isBlank()) {
                extension = "bin";
            }
            String name = basePath(documentFormData.table_name) + "/" + documentFormData.uuid + "." + extension;
            Map<String, Object> map = new HashMap<>();
            map.put(AppConstants.UUID, documentFormData.uuid);
            map.put(DOCUMENT_NAME, documentFormData.uuid + "." + extension);
            map.put(DOCUMENT_ORIGINAL_NAME, originalName != null ? originalName : documentFormData.uuid + "." + extension);
            map.put(DOCUMENT_PATH, name);
            map.put(DOCUMENT_MIME_TYPE, documentFormData.mimeType);
            map.put(SIZE, documentFormData.data.available());
            map.put(TABLE_NAME, documentFormData.table_name);
            map.put(TABLE_KEY, documentFormData.table_key);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(name)
                            .contentType(documentFormData.mimeType)
                            .stream(documentFormData.data, -1, PART_SIZE)
                            .build());
            Log.info("document uploaded!");
            return map;
        } catch (Exception e) {
            throw new Exception("Failed uploading file [{0}]", e);
        }
    }

    @Override
    public Map<String, Object> write(File file, String uuid, String table_name) throws Exception {
        Map<String, Object> map = new HashMap<>();
        String extension = ResourceFileUtils.getExtension(file.getName());
        String name = basePath(table_name) + "/" + uuid + "." + extension;
        minioClient.uploadObject(
                UploadObjectArgs.builder()
                        .bucket(bucketName)
                        .object(name)
                        .filename(file.getAbsolutePath())
                        .build());
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
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(name)
                        .contentType(contentType)
                        .stream(stream, -1, size)
                        .build());
        map.put(TABLE_NAME, table_name);
        map.put(DOCUMENT_PATH, name);
        return map;
    }

    @Override
    public boolean delete(String path) throws Exception {
        minioClient.statObject(
                StatObjectArgs.builder().bucket(bucketName).object(path).build());
        minioClient.removeObject(
                RemoveObjectArgs.builder().bucket(bucketName).object(path).build());
        return true;
    }

    @Override
    public File getFile(String path) throws Exception {
        String ext = ResourceFileUtils.getExtension(path);
        File temp = File.createTempFile(java.util.UUID.randomUUID().toString(), "." + ext);
        temp.deleteOnExit();
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(path)
                        .build())) {
            Files.copy(stream, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return temp;
    }

    @Override
    public StreamingOutput streamingOutput(String path, String mediatype) throws Exception {
        minioClient.statObject(
                StatObjectArgs.builder().bucket(bucketName).object(path).build());
        try {
            InputStream input = minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucketName).object(path).build());
            return output -> {
                byte[] buffer = new byte[BUFFER_SIZE]; // Adjust if you want
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                input.close();
            };
        } catch (Exception e) {
            throw new Exception("Failed downloading object with object name [{0}]", e);
        }
    }

    private void bucketExists() {
        try {
            boolean isExist = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (isExist) {
                Log.info("Bucket already exists.");
            } else {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void folderExists() {
        if (base_folder != null && !base_folder.trim().isEmpty()) {
            if (!base_folder.endsWith("/")) {
                base_folder = base_folder + "/";
            }
            Log.info("Bucket folder exists: " + base_folder);
        } else {
            Log.info("Bucket folder is empty or null");
            base_folder = null;
        }
    }
}
