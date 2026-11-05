package io.snello.api.service;

import io.snello.model.pojo.DocumentFormData;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.File;
import java.util.Map;

public interface StorageService {

    String basePath(String folder);

    Map<String, Object> upload(DocumentFormData documentFormData) throws Exception;


    Map<String, Object> write(File file,
                              String uuid,
                              String table_name) throws Exception;

    Map<String, Object> write(byte[] bytes, String uuid, String table_name, String extension) throws Exception;

    boolean delete(String path) throws Exception;

    File getFile(String path) throws Exception;

    StreamingOutput streamingOutput(String path, String mediatype) throws Exception;
}
