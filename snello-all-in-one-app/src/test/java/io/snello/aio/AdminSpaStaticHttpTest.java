package io.snello.aio;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Cache-Control the browser really gets, through a real router + StaticHandler (not just the helper). */
class AdminSpaStaticHttpTest {

    @TempDir
    static Path adminUi;

    static Vertx vertx;
    static HttpServer server;
    static final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void start() throws Exception {
        Files.writeString(adminUi.resolve("index.html"), "<!doctype html><script src=main-RIDEINPC.js></script>");
        Files.writeString(adminUi.resolve("main-RIDEINPC.js"), "import('./chunk-B-wx2uKE.js')");
        Files.writeString(adminUi.resolve("chunk-B-wx2uKE.js"), "export const x = 1");
        Files.createDirectories(adminUi.resolve("assets"));
        Files.writeString(adminUi.resolve("assets/logo.svg"), "<svg/>");

        vertx = Vertx.vertx();
        Router router = Router.router(vertx);
        AdminSpaStatic spa = new AdminSpaStatic();
        spa.adminUiPath = adminUi.toString();
        spa.register(router);
        server = vertx.createHttpServer().requestHandler(router).listen(0).toCompletionStage().toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
    }

    @AfterAll
    static void stop() throws Exception {
        server.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    static HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.actualPort() + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    static void assertCache(String path, int status, String cacheControl) throws Exception {
        HttpResponse<String> r = get(path);
        assertEquals(status, r.statusCode(), path);
        assertEquals(cacheControl, r.headers().firstValue("Cache-Control").orElse(null), path);
    }

    @Test
    void indexAndSpaRoutesRevalidate() throws Exception {
        assertCache("/", 200, AdminSpaStatic.NO_CACHE);
        assertCache("/index.html", 200, AdminSpaStatic.NO_CACHE);
        assertCache("/flowmonitor/list", 200, AdminSpaStatic.NO_CACHE);
        assertCache("/assets/logo.svg", 200, AdminSpaStatic.NO_CACHE);
    }

    @Test
    void hashedBundlesAreImmutable() throws Exception {
        assertCache("/main-RIDEINPC.js", 200, AdminSpaStatic.IMMUTABLE);
        assertCache("/chunk-B-wx2uKE.js", 200, AdminSpaStatic.IMMUTABLE);
    }

    @Test
    void missingChunkStays404() throws Exception {
        assertEquals(404, get("/chunk-CPi9WFRe.js").statusCode());
    }
}
