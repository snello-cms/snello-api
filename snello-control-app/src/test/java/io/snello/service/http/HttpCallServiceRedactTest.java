package io.snello.service.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** URLs are logged without query string or user-info, where API keys usually travel. */
class HttpCallServiceRedactTest {

    @Test
    void dropsQueryString() {
        assertEquals("https://api.x.com/v1/items?***", HttpCallService.redact("https://api.x.com/v1/items?key=SECRET&q=1"));
    }

    @Test
    void dropsUserInfoKeepsPort() {
        assertEquals("http://host:8443/p", HttpCallService.redact("http://user:pass@host:8443/p"));
    }

    @Test
    void plainUrlUnchanged() {
        assertEquals("https://api.x.com/v1", HttpCallService.redact("https://api.x.com/v1"));
    }

    @Test
    void malformedUrlStillHidesQuery() {
        assertEquals("not a url?***", HttpCallService.redact("not a url?token=SECRET"));
    }
}
