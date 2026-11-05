package io.snello.util;

import io.snello.model.pojo.DocumentFormData;
import jakarta.ws.rs.BadRequestException;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Optional;

public final class DocumentUtils {

    private DocumentUtils() {
    }

    public static void validateDocumentFormData(DocumentFormData documentFormData) {
        if (documentFormData == null) {
            throw new BadRequestException("multipart form data is missing");
        }
        if (!documentFormData.hasContent() && isBlank(documentFormData.remote_url)) {
            throw new BadRequestException("file part or remote_url is required");
        }
        if (isBlank(documentFormData.resolvedOriginalName())) {
            throw new BadRequestException("filename or original_name is required");
        }
        if (isBlank(documentFormData.mimeType)) {
            throw new BadRequestException("mimeType is required");
        }
        if (isBlank(documentFormData.table_name)) {
            throw new BadRequestException("table_name is required");
        }
        if (isBlank(documentFormData.table_key)) {
            throw new BadRequestException("table_key is required");
        }
    }

    /**
     * Se {@code documentFormData.remote_url} è valorizzato e {@code documentFormData.hasContent()} è false,
     * scarica il file remoto in un file temporaneo e popola i campi filename, original_name,
     * originalName, mimeType e localFile.
     *
     * @throws BadRequestException se lo schema dell'URL non è http o https (protezione SSRF)
     */
    public static void resolveRemoteFile(DocumentFormData documentFormData) throws Exception {
        if (documentFormData.hasContent() || isBlank(documentFormData.remote_url)) {
            return; // nulla da fare
        }

        URI uri = URI.create(documentFormData.remote_url.trim());
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new BadRequestException("remote_url must use http or https scheme");
        }

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        // ricava il mimeType dall'header Content-Type (solo la parte prima del ';')
        String contentType = response.headers().firstValue("Content-Type")
                .map(ct -> ct.split(";")[0].trim())
                .orElse(null);

        // ricava il filename dall'header Content-Disposition o dall'ultimo segmento dell'URL
        String detectedName = response.headers().firstValue("Content-Disposition")
                .flatMap(DocumentUtils::extractFilenameFromContentDisposition)
                .filter(n -> !n.isBlank())
                .orElseGet(() -> {
                    String path = uri.getPath();
                    int lastSlash = path.lastIndexOf('/');
                    return lastSlash >= 0 && lastSlash < path.length() - 1
                            ? path.substring(lastSlash + 1)
                            : uri.getHost();
                });

        // scrivi in file temporaneo per poter rileggere lo stream più volte
        File tmp = File.createTempFile("remote_", "_" + detectedName);
        tmp.deleteOnExit();
        try (InputStream in = response.body()) {
            Files.copy(in, tmp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        // popola i campi solo se non già impostati dal chiamante
        if (isBlank(documentFormData.mimeType) && contentType != null) {
            documentFormData.mimeType = contentType;
        }
        if (isBlank(documentFormData.filename)) {
            documentFormData.filename = detectedName;
        }
        if (isBlank(documentFormData.original_name)) {
            documentFormData.original_name = detectedName;
        }
        if (isBlank(documentFormData.originalName)) {
            documentFormData.originalName = detectedName;
        }

        // upload straight from the temp file; the caller deletes it through DocumentFormData.cleanup()
        documentFormData.localFile = tmp.toPath();
        documentFormData.localFileTemporary = true;
    }

    private static Optional<String> extractFilenameFromContentDisposition(String header) {
        for (String part : header.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("filename=")) {
                return Optional.of(trimmed.substring("filename=".length())
                        .replace("\"", "").trim());
            }
            if (trimmed.startsWith("filename*=")) {
                // es. filename*=UTF-8''encoded_name.pdf
                String value = trimmed.substring("filename*=".length());
                int tick = value.lastIndexOf("'");
                if (tick >= 0) {
                    value = value.substring(tick + 1);
                }
                try {
                    value = java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
                } catch (Exception ignored) {
                }
                return Optional.of(value.trim());
            }
        }
        return Optional.empty();
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static boolean hasFormatToken(String formats, String target) {
        if (formats == null || formats.isBlank() || target == null || target.isBlank()) {
            return false;
        }
        for (String token : formats.split(",")) {
            if (target.equalsIgnoreCase(token.trim())) {
                return true;
            }
        }
        return false;
    }
}