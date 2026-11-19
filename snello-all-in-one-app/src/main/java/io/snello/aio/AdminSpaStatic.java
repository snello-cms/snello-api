package io.snello.aio;

import io.quarkus.logging.Log;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.FileSystemAccess;
import io.vertx.ext.web.handler.StaticHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Serves the snello-admin SPA from a filesystem directory baked into the all-in-one image
 * ({@code /deployments/admin-ui} by default) and rewrites client-side routes to {@code index.html}.
 * <p>
 * Must run <em>before</em> the Quarkus REST catch-all (negative route order). When
 * {@link StaticHandler} does not find a file it calls {@code next()}; the following handler then
 * serves {@code index.html} for history-API paths so JAX-RS never turns them into JSON 404s.
 */
@ApplicationScoped
public class AdminSpaStatic {

    /** Before Quarkus REST / default static so unmatched UI paths never become JSON 404s first. */
    private static final int STATIC_ORDER = -2_000;

    static final String NO_CACHE = "no-cache";
    static final String IMMUTABLE = "public, max-age=31536000, immutable";
    /** {@code <name>-<8 char hash>.js|css} as written by the Angular application builder (the hash may contain - and _). */
    private static final Pattern HASHED_BUNDLE =
            Pattern.compile("^(main|chunk|polyfills|styles|scripts|runtime|worker)-[A-Za-z0-9_-]{8}\\.(js|mjs|css)$");

    @ConfigProperty(name = "snello.admin-ui.path", defaultValue = "/deployments/admin-ui")
    String adminUiPath;

    void register(@Observes Router router) {
        Path root = Path.of(adminUiPath);
        Path index = root.resolve("index.html");
        if (!Files.isDirectory(root) || !Files.isRegularFile(index)) {
            Log.warnf("snello-admin UI not found at %s — SPA not mounted (API-only mode)", adminUiPath);
            return;
        }

        StaticHandler files = StaticHandler.create(FileSystemAccess.ROOT, adminUiPath)
                .setIndexPage("index.html")
                .setDefaultContentEncoding("UTF-8")
                .setIncludeHidden(false)
                .setAlwaysAsyncFS(true);

        router.get().order(STATIC_ORDER).handler(ctx -> {
            String path = ctx.request().path();
            if (isReservedPath(path)) {
                ctx.next();
                return;
            }
            // StaticHandler's default (public, immutable, max-age=86400) also hit index.html: after a deploy the
            // browser kept the old index -> old main-*.js -> lazy chunk-*.js no longer in the image -> 404
            // ("error loading dynamically imported module"). Overridden when the headers are written.
            String cacheControl = cacheControl(path);
            ctx.addHeadersEndHandler(v -> ctx.response().putHeader("Cache-Control", cacheControl));
            files.handle(ctx);
        });

        // After StaticHandler.next() on a missing path: SPA routes → index.html; real assets stay 404.
        String indexPath = index.toString();
        router.get().order(STATIC_ORDER + 1).handler(ctx -> {
            String path = ctx.request().path();
            if (isReservedPath(path) || !isSpaFallbackPath(path)) {
                ctx.next();
                return;
            }
            ctx.response()
                    .putHeader("Content-Type", "text/html; charset=UTF-8")
                    .putHeader("Cache-Control", NO_CACHE)
                    .sendFile(indexPath);
        });

        Log.infof("snello-admin SPA mounted from %s (hashed bundles cached 1 year, index.html and the rest revalidated)",
                adminUiPath);
    }

    /**
     * Angular build outputs ({@code main-RIDEINPC.js}, {@code chunk-B-wx2uKE.js}, {@code styles-…css}, {@code media/…})
     * carry a content hash in the name, so they never change: cache for good. Everything else ({@code index.html},
     * {@code assets/…}, {@code favicon.ico}) is revalidated on every load (cheap 304 via Last-Modified).
     */
    static String cacheControl(String path) {
        if (path == null) {
            return NO_CACHE;
        }
        if (path.startsWith("/media/")) {
            return IMMUTABLE;
        }
        String leaf = path.substring(path.lastIndexOf('/') + 1);
        return HASHED_BUNDLE.matcher(leaf).matches() ? IMMUTABLE : NO_CACHE;
    }

    static boolean isReservedPath(String path) {
        if (path == null) {
            return true;
        }
        return path.startsWith("/api")
                || path.startsWith("/q/")
                || "/q".equals(path)
                || path.startsWith("/openapi")
                || path.startsWith("/agui")
                // workflow pages (PublicPagesRs): /p/{name}, /p/_/snello-page.js
                || path.startsWith("/p/");
    }

    static boolean isSpaFallbackPath(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return false;
        }
        if (isReservedPath(path)) {
            return false;
        }
        int slash = path.lastIndexOf('/');
        String leaf = slash >= 0 ? path.substring(slash + 1) : path;
        // Missing real assets (app.js, *.css, …) should stay 404, not return HTML.
        return !leaf.contains(".");
    }
}
