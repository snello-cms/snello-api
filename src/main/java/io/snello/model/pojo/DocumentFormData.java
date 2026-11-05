package io.snello.model.pojo;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DocumentFormData {
    /**
     * The uploaded file as a handle on the temp file Vert.x already spooled to disk. Deliberately NOT an
     * InputStream: storage services upload straight from the file, so the content never has to sit in heap.
     */
    @FormParam("file")
    public FileUpload file;

    /**
     * Content that did not come from the multipart "file" part (remote_url download, generated thumbnails).
     * Not bound from the request.
     */
    public Path localFile;

    /** When true {@link #localFile} is a temp file created by us and is deleted by {@link #cleanup()}. */
    public boolean localFileTemporary;

    @FormParam("uuid")
    @PartType(MediaType.TEXT_PLAIN)
    public String uuid;

    @FormParam("filename")
    @PartType(MediaType.TEXT_PLAIN)
    public String filename;

    @FormParam("original_name")
    @PartType(MediaType.TEXT_PLAIN)
    public String original_name;

    @FormParam("originalName")
    @PartType(MediaType.TEXT_PLAIN)
    public String originalName;

    @FormParam("mimeType")
    @PartType(MediaType.TEXT_PLAIN)
    public String mimeType;

    @FormParam("table_name")
    @PartType(MediaType.TEXT_PLAIN)
    public String table_name;

    @FormParam("table_key")
    @PartType(MediaType.TEXT_PLAIN)
    public String table_key;

    @FormParam("remote_url")
    @PartType(MediaType.TEXT_PLAIN)
    public String remote_url;

    /** Path of the file to store: the local file when set, otherwise the multipart upload. Null when there is none. */
    public Path sourcePath() {
        if (localFile != null) {
            return localFile;
        }
        return file != null ? file.uploadedFile() : null;
    }

    public boolean hasContent() {
        return sourcePath() != null;
    }

    /** Size in bytes of the content to store. */
    public long size() throws IOException {
        Path source = sourcePath();
        if (source == null) {
            throw new IOException("no content to store");
        }
        return Files.size(source);
    }

    /** Deletes the temp file we created (never the multipart one, Quarkus removes that when the request ends). */
    public void cleanup() {
        if (localFileTemporary && localFile != null) {
            try {
                Files.deleteIfExists(localFile);
            } catch (IOException e) {
                io.quarkus.logging.Log.warnf("unable to delete temp file %s: %s", localFile, e.getMessage());
            }
        }
    }

    public String resolvedOriginalName() {
        if (filename != null && !filename.isBlank()) {
            return filename;
        }
        if (original_name != null && !original_name.isBlank()) {
            return original_name;
        }
        if (originalName != null && !originalName.isBlank()) {
            return originalName;
        }
        return null;
    }
}
