package io.snello.test.metadata;

import io.quarkus.test.junit.QuarkusTest;
import io.snello.model.Metadata;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class MetadataRestTest {

    @Test
    void metadatasEndpointShouldReturnSeededData() {
        given()
                .accept(ContentType.JSON)
                .queryParam("_limit", 50)
                .when()
                .get("/api/metadatas")
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0))
                .body("[0].table_name", notNullValue());
    }

    @Test
    void metadataShouldBeAvailableThroughDatalistEndpoint() {
        List<Metadata> metadatas =
                given()
                        .accept(ContentType.JSON)
                        .queryParam("_limit", 50)
                        .when()
                        .get("/api/metadatas")
                        .then()
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getList("", Metadata.class);

        assertNotNull(metadatas);
        assertFalse(metadatas.isEmpty());

        String metadataName = metadatas.get(0).table_name;
        assertNotNull(metadataName);
        assertFalse(metadataName.isBlank());

        Metadata metadata =
                given()
                        .accept(ContentType.JSON)
                        .when()
                        .get("/api/datalist/metadata/{name}", metadataName)
                        .then()
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getObject("$", Metadata.class);

        assertNotNull(metadata);
        assertNotNull(metadata.table_name);
        assertTrue(!metadata.table_name.isBlank());
    }
}
