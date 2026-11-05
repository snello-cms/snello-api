package io.snello.service.storage;

import io.snello.api.service.StorageService;
import io.snello.model.pojo.DocumentFormData;

import jakarta.ws.rs.core.StreamingOutput;
import java.io.File;
import java.util.Map;

public class BlobStorageService implements StorageService {
    @Override
    public String basePath(String folder) {
        return null;
    }

    @Override
    public Map<String, Object> upload(DocumentFormData documentFormData) throws Exception {
        return null;
    }

    @Override
    public Map<String, Object> write(File file, String uuid, String table_name) throws Exception {
        return null;
    }

    @Override
    public Map<String, Object> write(byte[] bytes, String uuid, String table_name, String extension) throws Exception {
        return null;
    }

    @Override
    public boolean delete(String path) throws Exception {
        return false;
    }

    @Override
    public File getFile(String path) throws Exception {
        return null;
    }

    @Override
    public StreamingOutput streamingOutput(String path, String mediatype) throws Exception {
        return null;
    }
}
