package io.snello.test.metadata;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class FieldFacetDatalistTest {

    @Test
    void fielddefinitionsShouldReturnFlatLegacyRows() {
        String name = firstMetadataName();

        given()
                .accept(ContentType.JSON)
                .when()
                .get("/api/datalist/metadata/{name}/fielddefinitions", name)
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0))
                .body("[0].name", notNullValue())
                .body("[0]", hasKey("label"))
                .body("[0]", hasKey("type"))
                .body("[0]", hasKey("show_in_list"));
    }

    @Test
    void fieldmodelsShouldReturnFacetComposition() {
        String name = firstMetadataName();

        given()
                .accept(ContentType.JSON)
                .when()
                .get("/api/datalist/metadata/{name}/fieldmodels", name)
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0))
                .body("[0].name", notNullValue())
                .body("[0]", hasKey("query"))
                .body("[0]", hasKey("ui"))
                .body("[0]", hasKey("ai"));
    }

    @Test
    void fieldsUiAndQueryShouldExposeSingleConcern() {
        String name = firstMetadataName();

        List<Map<String, Object>> ui =
                given()
                        .accept(ContentType.JSON)
                        .when()
                        .get("/api/datalist/metadata/{name}/fields/ui", name)
                        .then()
                        .statusCode(200)
                        .body("size()", greaterThan(0))
                        .body("[0].name", notNullValue())
                        .body("[0]", hasKey("ui"))
                        .extract()
                        .jsonPath()
                        .getList("$");

        assertFalse(ui.isEmpty());
        assertTrue(ui.get(0).containsKey("ui"));
        assertFalse(ui.get(0).containsKey("query"));

        given()
                .accept(ContentType.JSON)
                .when()
                .get("/api/datalist/metadata/{name}/fields/query", name)
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0))
                .body("[0].name", notNullValue());

        given()
                .accept(ContentType.JSON)
                .when()
                .get("/api/datalist/metadata/{name}/ui", name)
                .then()
                .statusCode(200)
                .body("tableName", notNullValue())
                .body("$", hasKey("ui"))
                .body("$", hasKey("fields"));

        given()
                .accept(ContentType.JSON)
                .when()
                .get("/api/datalist/metadata/{name}/ai", name)
                .then()
                .statusCode(200)
                .body("tableName", notNullValue())
                .body("$", hasKey("ai"));
    }

    @Test
    void metadataDatalistShouldReturnFlatLegacyMetadata() {
        String name = firstMetadataName();

        given()
                .accept(ContentType.JSON)
                .when()
                .get("/api/datalist/metadata/{name}", name)
                .then()
                .statusCode(200)
                .body("table_name", notNullValue())
                .body("icon", notNullValue());
    }

    @Test
    void metadataModelShouldReturnEntityFacets() {
        String name = firstMetadataName();

        given()
                .accept(ContentType.JSON)
                .when()
                .get("/api/datalist/metadata/{name}/model", name)
                .then()
                .statusCode(200)
                .body("identity.table_name", notNullValue())
                .body("$", hasKey("query"))
                .body("$", hasKey("ui"))
                .body("$", hasKey("ai"));
    }

    private static String firstMetadataName() {
        List<String> names =
                given()
                        .accept(ContentType.JSON)
                        .when()
                        .get("/api/datalist/names")
                        .then()
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getList("$", String.class);
        assertNotNull(names);
        assertFalse(names.isEmpty());
        return names.get(0);
    }
}
