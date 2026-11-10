package io.snello.aio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminConfigRewriteTest {

    @Test
    void stripOriginKeepsPath() {
        assertEquals("/api", AdminConfigRewrite.stripOrigin("https://kayak.love/api"));
        assertEquals("/api/metadatas", AdminConfigRewrite.stripOrigin("https://kayak.love/api/metadatas"));
        assertEquals("/", AdminConfigRewrite.stripOrigin("https://accounts.kayak.love/"));
        assertEquals("/api", AdminConfigRewrite.stripOrigin("/api"));
    }
}
