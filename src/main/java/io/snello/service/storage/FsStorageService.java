package io.snello.service.storage;

import io.quarkus.logging.Log;
import io.snello.api.service.StorageService;
import io.snello.management.AppConstants;
import io.snello.model.pojo.DocumentFormData;
import io.snello.util.ResourceFileUtils;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import static io.snello.management.AppConstants.*;

public class FsStorageService implements StorageService {

    // download copy buffer: 64 KB is ~64x fewer read/write calls than 1 KB and still tiny per concurrent download
    private static final int BUFFER_SIZE = 64 * 1024;

    String basePath;

    public FsStorageService(String basePath) {
        this.basePath = basePath;
    }


    @Override
    public String basePath(String folder) {
        if (folder != null)
            return addSlash(basePath.replace("file:", "").replace("\"", "")) + folder;
        return basePath;
    }

    @Override
    public Map<String, Object> upload(DocumentFormData documentFormData) throws Exception {
        Path path = verifyPath(documentFormData.table_name);
        String originalName = documentFormData.resolvedOriginalName();
        String extension = ResourceFileUtils.getExtension(originalName);
        if (extension == null || extension.isBlank()) {
            extension = "bin";
        }
        File tempFile = File.createTempFile(documentFormData.uuid, "." + extension, path.toFile());
        Path source = documentFormData.sourcePath();
        if (source == null) {
            throw new IllegalArgumentException("no file content to store");
        }
        Files.copy(source, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Map<String, Object> map = new HashMap<>();
        map.put(AppConstants.UUID, documentFormData.uuid);
        map.put(DOCUMENT_NAME, tempFile.getName());
        map.put(DOCUMENT_ORIGINAL_NAME, originalName != null ? originalName : documentFormData.uuid + "." + extension);
        map.put(DOCUMENT_PATH, tempFile.getParentFile().getName() + "/" + tempFile.getName());
        map.put(DOCUMENT_MIME_TYPE, documentFormData.mimeType);
        map.put(SIZE, documentFormData.size());
        map.put(TABLE_NAME, documentFormData.table_name);
        map.put(TABLE_KEY, documentFormData.table_key);
        return map;
    }

    @Override
    public Map<String, Object> write(File file, String uuid, String table_name) throws Exception {
        Map<String, Object> map = new HashMap<>();
        Path path = verifyPath(table_name);
        String extension = ResourceFileUtils.getExtension(file.getName());
        File copied = File.createTempFile(uuid, "." + extension, path.toFile());
        Files.copy(file.toPath(), copied.toPath(), StandardCopyOption.REPLACE_EXISTING);
        map.put(TABLE_NAME, table_name);
        map.put(DOCUMENT_PATH, copied.getParentFile().getName() + "/" + copied.getName());

        return map;
    }

    @Override
    public Map<String, Object> write(byte[] bytes, String uuid, String table_name, String extension) throws Exception {
        Map<String, Object> map = new HashMap<>();
        Path path = verifyPath(table_name);
        File copied = File.createTempFile(uuid, "." + extension, path.toFile());
        try (FileOutputStream stream = new FileOutputStream(copied)) {
            stream.write(bytes);
        }
        map.put(TABLE_NAME, table_name);
        map.put(DOCUMENT_PATH, copied.getParentFile().getName() + "/" + copied.getName());
        return map;
    }

    @Override
    public boolean delete(String deletePath) throws Exception {
        Path path = Paths.get(basePath, deletePath);
        Files.delete(path);
        return true;
    }

    @Override
    public File getFile(String path) throws Exception {
        return Paths.get(basePath, path).toFile();
    }

    @Override
    public StreamingOutput streamingOutput(String path, String mediatype) throws Exception {
        try {
            InputStream input =Files.newInputStream(Paths.get(basePath, path));
            return output -> {
                // try-with-resources: the file handle is released even when the client disconnects mid-download
                try (input) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    while ((bytesRead = input.read(buffer)) != -1) {
                        output.write(buffer, 0, bytesRead);
                    }
                }
            };
        } catch (Exception e) {
            throw new Exception("Failed downloading object with object name [{0}]", e);
        }
    }

    private String addSlash(String path) {
        if (path.endsWith("/")) {
            return path;
        }
        return path + "/";
    }

    private Path verifyPath(String table_name) throws Exception {
        Path path = Path.of(basePath(table_name));
        if (Files.exists(path)) {
            Log.info("path already existent: " + path);
        } else {
            path = Files.createDirectory(path);
        }
        return path;
    }
}
