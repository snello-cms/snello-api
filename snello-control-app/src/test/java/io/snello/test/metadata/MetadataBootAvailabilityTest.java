package io.snello.test.metadata;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThan;

@QuarkusTest
class MetadataBootAvailabilityTest {

    @Test
    void snelloShouldBeUpAndServeMetadataSearchAtBoot() {
        given()
                .accept(ContentType.JSON)
                .queryParam("_limit", 10)
                .queryParam("_sort", "table_name asc")
                .when()
                .get("/api/metadatas")
                .then()
                .statusCode(200)
                .header("x-total-count", greaterThan("0"));
    }
}
