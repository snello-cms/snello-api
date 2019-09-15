package io.snello.service.documents.fs;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import io.snello.management.AppConstants;
import io.snello.service.documents.DocumentsService;
import io.snello.util.ResourceFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.snello.management.AppConstants.*;

@Singleton
@Requires(property = STORAGE_TYPE, value = "fs")
public class FsService implements DocumentsService {


    Logger logger = LoggerFactory.getLogger(FsService.class);

    @Property(name = SYSTEM_DOCUMENTS_BASE_PATH)
    List<String> basePaths;

    @EventListener
    public void onStartup(ServerStartupEvent event) {
        logger.info("FsService load");
    }

    @Override
    public String basePath(String folder) {
        String basePath = basePaths.get(0);
        if (folder != null)
            return addSlash(basePath.replace("file:", "").replace("\"", "")) + folder;
        return basePath;
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
            logger.info("path already existent: " + path);
        } else {
            path = Files.createDirectory(path);
        }
        return path;
    }

    @Override
    public Map<String, Object> upload(CompletedFileUpload file, String uuid, String table_name, String table_key) throws Exception {
        Path path = verifyPath(table_name);
        String extension = file.getContentType().get().getExtension();
        File tempFile = File.createTempFile(uuid, "." + extension, path.toFile());
        Files.write(tempFile.toPath(), file.getBytes());
        Map<String, Object> map = new HashMap<>();
        map.put(AppConstants.UUID, uuid);
        map.put(DOCUMENT_NAME, tempFile.getName());
        map.put(DOCUMENT_ORIGINAL_NAME, file.getFilename());
        map.put(DOCUMENT_PATH, tempFile.getParentFile().getName() + "/" + tempFile.getName());
        map.put(DOCUMENT_MIME_TYPE, file.getContentType().get().getName());
        map.put(SIZE, file.getSize());
        map.put(TABLE_NAME, table_name);
        map.put(TABLE_KEY, table_key);
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
    public boolean delete(String filepath) throws Exception {
        String basePath = basePaths.get(0);
        Path path = Paths.get(basePath, filepath);
        Files.delete(path);
        return true;
    }

    @Override
    public File getFile(String path) throws Exception {
        String basePath = basePaths.get(0);
        return Paths.get(basePath, path).toFile();
    }

    @Override
    public StreamedFile streamingOutput(String path, String mediatype) throws Exception {
        String basePath = basePaths.get(0);
        InputStream input = Files.newInputStream(Paths.get(basePath, path));
        return new StreamedFile(input, new MediaType(mediatype));
    }


}
