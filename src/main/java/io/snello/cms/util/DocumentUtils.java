package io.snello.cms.util;

import io.micronaut.http.multipart.CompletedFileUpload;
import io.snello.cms.management.AppConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static io.snello.cms.management.AppConstants.*;

public class DocumentUtils {

    static Logger logger = LoggerFactory.getLogger(DocumentUtils.class);


    public static Path basePath(String folder, String basePath) {
        if (folder != null)
            return Path.of(basePath.replace("file:", "") + folder);
        return Path.of(basePath);
    }

    public static Map<String, Object> work(CompletedFileUpload file,
                                           String uuid,
                                           String basePath,
                                           String table,
                                           String table_key) throws Exception {
        Path path = DocumentUtils.basePath(table, basePath);
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
        map.put(DOCUMENT_SIZE, file.getSize());
        map.put(DOCUMENT_TABLE, table);
        map.put(DOCUMENT_TABLE_KEY, table_key);
        return map;
    }
}
