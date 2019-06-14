package io.snello.controller;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.snello.model.ResourceFile;
import io.snello.util.JsonUtils;
import io.snello.util.ResourceFileUtils;
import io.snello.util.ZipUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static io.micronaut.http.HttpResponse.ok;
import static io.snello.management.AppConstants.*;

@Controller(PUBLIC_DATA_PATH)
public class PublicDataController {

    @Property(name = SYSTEM_DOCUMENTS_BASE_PATH)
    List<String> basePaths;

    Logger logger = LoggerFactory.getLogger(PublicDataController.class);


    @Post(consumes = MediaType.MULTIPART_FORM_DATA)
    public HttpResponse<?> post(CompletedFileUpload file) {
        if (file == null || !file.getFilename().endsWith(ZIP)) {
            return null;
        }
        try {
            String basePath = basePaths.get(0);
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
            File tempFile = File.createTempFile(java.util.UUID.randomUUID().toString(), DOT_ZIP);
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

    @Get("/folders/{folderEncoded}")
    public HttpResponse<?> resourceFileList(@Nullable String folderEncoded) {
        String folderName = null;
        if (folderEncoded != null && !folderEncoded.trim().isEmpty()) {
            byte[] decodedBytes = Base64.getDecoder().decode(folderEncoded);
            folderName = new String(decodedBytes);
        } else {
            folderName = basePaths.get(0);
        }
        try {
            File[] allContents = new File(folderName).listFiles();
            return ok(ResourceFileUtils.fromFiles(allContents));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return HttpResponse.serverError();
    }

    @Post("/folders/{folderEncoded}")
    public HttpResponse<?> createFolder(@Nullable String folderEncoded, @Body String body) throws Exception {
        Map<String, Object> map = JsonUtils.fromJson(body);
        String folderName = null;
        if (folderEncoded != null && !folderEncoded.trim().isEmpty()) {
            byte[] decodedBytes = Base64.getDecoder().decode(folderEncoded);
            folderName = new String(decodedBytes);
        } else {
            folderName = basePaths.get(0);
        }
        try {
            ResourceFile resourceFile = new ResourceFile(map, folderName);
            File file = new File(resourceFile.path);
            boolean rs = file.mkdir();
            return ok(rs);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return HttpResponse.serverError();
    }

    @Delete("/folders/{folderEncoded}")
    public HttpResponse<?> deleteFolderOrFile(@NotNull String folderEncoded) throws Exception {
        String folderName = null;
        if (folderEncoded != null && !folderEncoded.trim().isEmpty()) {
            byte[] decodedBytes = Base64.getDecoder().decode(folderEncoded);
            folderName = new String(decodedBytes);
        }
        try {
            File file = new File(folderName);
            ResourceFileUtils.deleteDir(file);
            return ok();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return HttpResponse.serverError();
    }

    @Put("/folders/{folderEncoded}/rename/{newName}")
    public HttpResponse<?> renameFolderOrFile(@NotNull String folderEncoded, @NotNull String newName) throws Exception {
        byte[] decodedBytes = Base64.getDecoder().decode(folderEncoded);
        String folderName = new String(decodedBytes);
        try {
            File file = new File(folderName);
            String newfolderName = folderName.replace(file.getName(), newName);
            File newFile = new File(newfolderName);
            file.renameTo(newFile);
            return ok();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return HttpResponse.serverError();
    }

    @Post("/folders/{folderEncoded}/files")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public HttpResponse<?> createFile(@Nullable String folderEncoded, CompletedFileUpload file) {
        if (file == null) {
            return null;
        }
        try {
            String folderName = null;
            if (folderEncoded != null && !folderEncoded.trim().isEmpty()) {
                byte[] decodedBytes = Base64.getDecoder().decode(folderEncoded);
                folderName = new String(decodedBytes);
            } else {
                folderName = basePaths.get(0);
            }
            Files.write(Path.of(folderName), file.getBytes());
            return ok();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return HttpResponse.serverError();
    }

}
