package io.snello.aio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminSpaStaticTest {

    @Test
    void spaFallbackSkipsApiAndAssets() {
        assertFalse(AdminSpaStatic.isSpaFallbackPath("/"));
        assertFalse(AdminSpaStatic.isSpaFallbackPath("/api/metadatas"));
        assertFalse(AdminSpaStatic.isSpaFallbackPath("/openapi"));
        assertFalse(AdminSpaStatic.isSpaFallbackPath("/q/health"));
        assertFalse(AdminSpaStatic.isSpaFallbackPath("/agui"));
        assertFalse(AdminSpaStatic.isSpaFallbackPath("/assets/app.js"));
        assertTrue(AdminSpaStatic.isSpaFallbackPath("/dashboard"));
        assertTrue(AdminSpaStatic.isSpaFallbackPath("/metadata/boats"));
    }

    @Test
    void onlyHashedBundlesAreCachedForGood() {
        assertEquals(AdminSpaStatic.IMMUTABLE, AdminSpaStatic.cacheControl("/main-RIDEINPC.js"));
        assertEquals(AdminSpaStatic.IMMUTABLE, AdminSpaStatic.cacheControl("/chunk-B-wx2uKE.js"));
        assertEquals(AdminSpaStatic.IMMUTABLE, AdminSpaStatic.cacheControl("/chunk-BEPR7yp_.js"));
        assertEquals(AdminSpaStatic.IMMUTABLE, AdminSpaStatic.cacheControl("/styles-ABCD1234.css"));
        assertEquals(AdminSpaStatic.IMMUTABLE, AdminSpaStatic.cacheControl("/media/fa-solid-900-XYZ.woff2"));
        assertEquals(AdminSpaStatic.NO_CACHE, AdminSpaStatic.cacheControl("/"));
        assertEquals(AdminSpaStatic.NO_CACHE, AdminSpaStatic.cacheControl("/index.html"));
        assertEquals(AdminSpaStatic.NO_CACHE, AdminSpaStatic.cacheControl("/assets/config.json"));
        assertEquals(AdminSpaStatic.NO_CACHE, AdminSpaStatic.cacheControl("/assets/img/logo.png"));
        assertEquals(AdminSpaStatic.NO_CACHE, AdminSpaStatic.cacheControl("/favicon.ico"));
        assertEquals(AdminSpaStatic.NO_CACHE, AdminSpaStatic.cacheControl(null));
    }

    @Test
    void reservedPathsSkipStaticHandler() {
        assertTrue(AdminSpaStatic.isReservedPath("/api/metadatas"));
        assertTrue(AdminSpaStatic.isReservedPath("/openapi"));
        assertTrue(AdminSpaStatic.isReservedPath("/q/health"));
        assertTrue(AdminSpaStatic.isReservedPath("/p/task"));
        assertTrue(AdminSpaStatic.isReservedPath("/p/_/snello-page.js"));
        assertTrue(AdminSpaStatic.isReservedPath("/agui"));
        assertFalse(AdminSpaStatic.isReservedPath("/"));
        assertFalse(AdminSpaStatic.isReservedPath("/dashboard"));
        assertFalse(AdminSpaStatic.isReservedPath("/assets/app.js"));
    }
}
