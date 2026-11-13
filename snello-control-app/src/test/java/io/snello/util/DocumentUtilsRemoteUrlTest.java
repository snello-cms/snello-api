package io.snello.util;

import com.sun.net.httpserver.HttpServer;
import io.snello.model.pojo.DocumentFormData;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain unit test (no Quarkus): {@link DocumentUtils#resolveRemoteFile} stores only a 2xx body. A 4xx/5xx answer
 * (e.g. a print template's 400 {"error": ...}) must fail the upload instead of replacing a good document with it.
 */
class DocumentUtilsRemoteUrlTest {

    private static HttpServer server;
    private static String base;

    @BeforeAll
    static void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", ex -> {
            byte[] body = "<html><body>report</body></html>".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            ex.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"report.html\"");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/broken", ex -> {
            byte[] body = "{\"error\":\"TemplateException: Rendering error\"}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(400, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/missing", ex -> {
            ex.sendResponseHeaders(404, -1);
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    private static DocumentFormData form(String path) {
        DocumentFormData f = new DocumentFormData();
        f.remote_url = base + path;
        f.table_name = "events";
        f.table_key = "report-test";
        return f;
    }

    @Test
    void okBodyIsDownloadedAndNamedFromHeaders() throws Exception {
        DocumentFormData f = form("/ok");
        try {
            DocumentUtils.resolveRemoteFile(f);
            assertTrue(f.hasContent());
            assertEquals("text/html", f.mimeType);
            assertEquals("report.html", f.filename);
            assertTrue(Files.readString(f.sourcePath()).contains("report"));
        } finally {
            f.cleanup();
        }
    }

    @Test
    void callerNamesAndTypeWinOverHeaders() throws Exception {
        DocumentFormData f = form("/ok");
        f.filename = "events-2026-report.html";
        f.mimeType = "text/html";
        try {
            DocumentUtils.resolveRemoteFile(f);
            assertEquals("events-2026-report.html", f.filename);
        } finally {
            f.cleanup();
        }
    }

    @Test
    void errorStatusIsRefusedWithItsBody() {
        DocumentFormData f = form("/broken");
        BadRequestException e = assertThrows(BadRequestException.class, () -> DocumentUtils.resolveRemoteFile(f));
        assertTrue(e.getMessage().contains("HTTP 400"), e.getMessage());
        assertTrue(e.getMessage().contains("TemplateException"), e.getMessage());
        assertFalse(f.hasContent(), "nothing must be staged for upload");
    }

    @Test
    void notFoundIsRefused() {
        DocumentFormData f = form("/missing");
        BadRequestException e = assertThrows(BadRequestException.class, () -> DocumentUtils.resolveRemoteFile(f));
        assertTrue(e.getMessage().contains("HTTP 404"), e.getMessage());
    }

    @Test
    void nonHttpSchemeIsStillRefused() {
        DocumentFormData f = form("/ok");
        f.remote_url = "file:///etc/passwd";
        assertThrows(BadRequestException.class, () -> DocumentUtils.resolveRemoteFile(f));
    }

    @Test
    void redactDropsQueryString() {
        assertEquals("https://api.sne.lol/api/printtemplates/x/print?***",
                DocumentUtils.redact(URI.create("https://api.sne.lol/api/printtemplates/x/print?token=secret")));
        assertEquals("http://h:8080/a", DocumentUtils.redact(URI.create("http://h:8080/a")));
    }
}
