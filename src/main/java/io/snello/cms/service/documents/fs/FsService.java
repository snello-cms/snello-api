package io.snello.cms.service.documents.fs;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import io.snello.cms.management.AppConstants;
import io.snello.cms.service.documents.DocumentsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static io.snello.cms.management.AppConstants.*;

@Singleton
@Requires(property = STORAGE_TYPE, value = "fs")
public class FsService implements DocumentsService {


    Logger logger = LoggerFactory.getLogger(FsService.class);

    @Property(name = SYSTEM_DOCUMENTS_BASE_PATH)
    String basePath;

    @EventListener
    public void onStartup(ServerStartupEvent event) {
        logger.info("FsService load");
    }

    @Override
    public String basePath(String folder) {
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

    @Override
    public Map<String, Object> upload(CompletedFileUpload file, String uuid, String table_name, String table_key) throws Exception {
        Path path = Path.of(basePath(table_name));
        if (Files.exists(path)) {
            logger.info("path already existent: " + path);
        } else {
            path = Files.createDirectory(path);
        }
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
    public boolean delete(String filepath) throws Exception {
        Path path = Paths.get(basePath, filepath);
        Files.delete(path);
        return true;
    }

    @Override
    public StreamedFile streamingOutput(String uuid, String folder, String mediatype) throws Exception {
        InputStream input = Files.newInputStream(Paths.get(basePath, folder + uuid));
        return new StreamedFile(input, new MediaType(mediatype));
    }
}
