package io.snello.service.http;

import io.snello.model.pojo.HttpCallResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Plain unit test (no Quarkus): the SSRF guard of {@link HttpCallService}. */
class HttpCallGuardTest {

    private HttpCallService service(boolean allowPrivate, List<String> allowed) {
        HttpCallService s = new HttpCallService();
        s.allowPrivate = allowPrivate;
        s.allowedHosts = Optional.ofNullable(allowed);
        s.timeoutSeconds = 3;
        return s;
    }

    @Test
    void privateTargetsAreRefusedByDefault() {
        for (String url : List.of("http://127.0.0.1:8080/x", "http://localhost/x", "http://169.254.169.254/latest/meta-data",
                "http://10.0.0.5/x", "http://192.168.1.4/x")) {
            HttpCallResult r = service(false, null).call("GET", url);
            assertEquals(0, r.status(), url);
            assertTrue(r.error().contains("private address"), url + " -> " + r.error());
        }
    }

    @Test
    void nonHttpSchemesAndBadMethodsAreRefused() {
        assertTrue(service(true, null).call("GET", "file:///etc/passwd").error().contains("only http/https"));
        assertTrue(service(true, null).call("TRACE", "http://example.com").error().contains("unsupported method"));
    }

    @Test
    void allowedHostsListRestrictsTargets() {
        HttpCallResult r = service(true, List.of("api.good.com")).call("GET", "http://evil.example/x");
        assertTrue(r.error().contains("allowed-hosts"), r.error());
    }
}
