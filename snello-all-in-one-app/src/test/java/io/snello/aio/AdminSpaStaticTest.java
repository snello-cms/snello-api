package io.snello.aio;

import org.junit.jupiter.api.Test;

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
    void reservedPathsSkipStaticHandler() {
        assertTrue(AdminSpaStatic.isReservedPath("/api/metadatas"));
        assertTrue(AdminSpaStatic.isReservedPath("/openapi"));
        assertTrue(AdminSpaStatic.isReservedPath("/q/health"));
        assertTrue(AdminSpaStatic.isReservedPath("/agui"));
        assertFalse(AdminSpaStatic.isReservedPath("/"));
        assertFalse(AdminSpaStatic.isReservedPath("/dashboard"));
        assertFalse(AdminSpaStatic.isReservedPath("/assets/app.js"));
    }
}
