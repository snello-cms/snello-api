package io.snello.util;

import io.quarkus.logging.Log;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static io.snello.management.AppConstants.DOCUMENT_NAME;
import static io.snello.management.AppConstants.DOCUMENT_ORIGINAL_NAME;

public final class ContentDispositionUtils {

    private ContentDispositionUtils() {
    }

    /**
     * The name the user uploaded ({@code original_name}); falls back to the stored name ({@code uuid.ext}).
     */
    public static String downloadName(Map<String, Object> document) {
        Object original = document.get(DOCUMENT_ORIGINAL_NAME);
        if (original != null && !original.toString().isBlank()) {
            return original.toString();
        }
        Object stored = document.get(DOCUMENT_NAME);
        Log.infof("document has no original_name, falling back to stored name=%s", stored);
        return stored != null ? stored.toString() : "download";
    }

    /**
     * Builds a Content-Disposition value that is safe for quotes/CR/LF in the name and keeps non-ASCII names
     * intact: an ASCII fallback in {@code filename} plus the RFC 5987 {@code filename*} form.
     *
     * @param type "attachment" or "inline"
     */
    public static String of(String type, String filename) {
        String clean = filename.replaceAll("[\\r\\n]", "").replace("\\", "_");
        String ascii = clean.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "'");
        String encoded = URLEncoder.encode(clean, StandardCharsets.UTF_8).replace("+", "%20");
        return type + "; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }
}
