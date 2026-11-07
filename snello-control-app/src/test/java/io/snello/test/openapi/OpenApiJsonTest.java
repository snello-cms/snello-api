package io.snello.test.openapi;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class OpenApiJsonTest {

    @Test
    void openapiJsonListsRestPaths() {
        Map<?, ?> doc = given()
                .accept("application/json")
                .when()
                .get("/openapi")
                .then()
                .statusCode(200)
                .contentType(containsString("application/json"))
                .body("openapi", startsWith("3."))
                .body("paths", notNullValue())
                .extract()
                .jsonPath()
                .getMap("");

        @SuppressWarnings("unchecked")
        Map<String, Object> paths = (Map<String, Object>) doc.get("paths");
        assertNotNull(paths);
        assertFalse(paths.isEmpty(), "expected discovered JAX-RS paths");
        assertTrue(paths.keySet().stream().anyMatch(p -> p.contains("/api/metadatas")),
                "expected /api/metadatas in paths: " + paths.keySet());
        assertTrue(paths.keySet().stream().anyMatch(p -> p.contains("/api/datalist")),
                "expected /api/datalist in paths: " + paths.keySet());
    }
}
