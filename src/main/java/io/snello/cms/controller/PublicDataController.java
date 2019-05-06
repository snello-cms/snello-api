package io.snello.cms.controller;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.snello.util.ZipUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static io.micronaut.http.HttpResponse.ok;
import static io.snello.cms.management.AppConstants.PUBLIC_DATA_PATH;
import static io.snello.cms.management.AppConstants.SYSTEM_DOCUMENTS_BASE_PATH;
import static io.snello.cms.management.AppConstants.ZIP;
import static io.snello.cms.management.AppConstants.DOT_ZIP;
import static io.snello.cms.management.AppConstants.FILES;
import static io.snello.cms.management.AppConstants.EMPTY;
import static io.snello.cms.management.AppConstants.FILE_DOT_DOT;

@Controller(PUBLIC_DATA_PATH)
public class PublicDataController {

    @Property(name = SYSTEM_DOCUMENTS_BASE_PATH)
    String basePath;

    Logger logger = LoggerFactory.getLogger(PublicDataController.class);


    @Post(consumes = MediaType.MULTIPART_FORM_DATA)
    public HttpResponse<?> post(CompletedFileUpload file) {
        if (file == null || !file.getFilename().endsWith(ZIP)) {
            return null;
        }
        try {
            //devo usare la cartella sopra quella dei files
            Path path = Path.of(basePath.replace(FILE_DOT_DOT, EMPTY)
                    .replace("\"", EMPTY)
                    .replace(FILES, EMPTY)
            );
            Map<String, Object> map = null;
            File[] allContents = path.toFile().listFiles();
            if (allContents != null) {
                for (File contentFile : allContents) {
                    logger.info("file: " + contentFile.getName());
                    if (contentFile.getName().equals(FILES)) {
                        continue;
                    }
                    logger.info("to be deleted: " + contentFile.getName());
                    if (contentFile.isDirectory()) {
                        deleteDirectory(contentFile);
                    } else {
                        contentFile.delete();
                    }
                }
            }
            File tempFile = File.createTempFile(UUID.randomUUID().toString(), DOT_ZIP);
            Files.write(tempFile.toPath(), file.getBytes());
            ZipUtils.unzip(tempFile.getAbsolutePath(), path.toFile().getAbsolutePath());
            tempFile.delete();
            return ok();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return HttpResponse.serverError();
    }

    boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }

}
