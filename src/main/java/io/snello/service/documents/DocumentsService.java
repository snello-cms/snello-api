package io.snello.service.documents;

import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.server.types.files.StreamedFile;

import java.io.File;
import java.util.Map;

public interface DocumentsService {


    String basePath(String folder);

    Map<String, Object> upload(CompletedFileUpload file,
                               String uuid,
                               String table_name,
                               String table_key) throws Exception;


    Map<String, Object> write(File file,
                 String uuid,
                 String table_name) throws Exception;

    Map<String, Object> write(byte[] bytes, String uuid, String table_name, String extension) throws Exception;

    boolean delete(String path) throws Exception;

    File getFile(String path) throws Exception;

    StreamedFile streamingOutput(String path, String mediatype) throws Exception;
}
