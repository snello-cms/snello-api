package io.snello.test.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;

@QuarkusTest
class ApiServiceRsNullSafetyTest {

    @Test
    void shouldReturnNotFoundWhenPostTableMetadataIsMissing() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("name", "value"))
                .when()
                .post("/api/not_existing_table_for_test")
                .then()
                .statusCode(404);
    }

    @Test
    void shouldReturnNotFoundWhenPutTableMetadataIsMissing() {
        String randomUuid = UUID.randomUUID().toString();

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("name", "value"))
                .when()
                .put("/api/not_existing_table_for_test/{uuid}", randomUuid)
                .then()
                .statusCode(404);
    }
}
