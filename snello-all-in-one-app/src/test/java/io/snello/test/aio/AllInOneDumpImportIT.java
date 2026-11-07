package io.snello.test.aio;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Black-box IT against a running {@code snello-all-in-one} container.
 * <p>
 * Prerequisites: {@code compose-allinone-test.yml} up, fixture JSON from the
 * dump export ({@code metadatas-from-dump.json}). Orchestrated by
 * {@code dev/scripts/run-aio-import-tests.sh}.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfSystemProperty(named = "aio.baseUrl", matches = ".+")
class AllInOneDumpImportIT {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static Path fixturePath;
    private static int exportedCount;

    @BeforeAll
    static void configure() throws Exception {
        String base = System.getProperty("aio.baseUrl");
        RestAssured.baseURI = base;
        String fixture = System.getProperty(
                "aio.fixture",
                "src/test/resources/fixtures/metadatas-from-dump.json");
        fixturePath = Path.of(fixture);
        if (!fixturePath.isAbsolute()) {
            // resolve relative to all-in-one module when run from reactor
            Path moduleLocal = Path.of("snello-all-in-one-app").resolve(fixture);
            if (Files.isRegularFile(moduleLocal)) {
                fixturePath = moduleLocal;
            }
        }
        assertTrue(Files.isRegularFile(fixturePath),
                "fixture missing: " + fixturePath.toAbsolutePath()
                        + " — run ./dev/scripts/generate-metadatas-export.sh");

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = MAPPER.readValue(Files.readAllBytes(fixturePath), Map.class);
        @SuppressWarnings("unchecked")
        List<?> metadatas = (List<?>) payload.get("metadatas");
        assertNotNull(metadatas);
        exportedCount = metadatas.size();
        assertTrue(exportedCount > 0, "export fixture must contain metadatas");
    }

    @Test
    @Order(1)
    void healthOrDatalistIsReachable() {
        int status = given()
                .when()
                .get("/api/datalist/names")
                .then()
                .extract()
                .statusCode();
        assertTrue(status == 200 || status == 204, "datalist names status=" + status);
    }

    @Test
    @Order(2)
    void importDumpExportIntoAllInOne() throws Exception {
        byte[] json = Files.readAllBytes(fixturePath);

        @SuppressWarnings("unchecked")
        List<Map> imported =
                given()
                        .contentType(ContentType.JSON)
                        .body(json)
                        .when()
                        .post("/api/metadatas/import")
                        .then()
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getList("imported", Map.class);

        assertNotNull(imported);
        assertEquals(exportedCount, imported.size(),
                "imported count should match export fixture size");
    }

    @Test
    @Order(3)
    void reimportShouldConflict() throws Exception {
        byte[] json = Files.readAllBytes(fixturePath);
        given()
                .contentType(ContentType.JSON)
                .body(json)
                .when()
                .post("/api/metadatas/import")
                .then()
                .statusCode(409);
    }

    @Test
    @Order(4)
    void datalistExposesKayaksFacets() {
        given()
                .when()
                .get("/api/datalist/names")
                .then()
                .statusCode(200)
                .body("$", hasItem("kayaks"));

        given()
                .when()
                .get("/api/datalist/metadata/kayaks")
                .then()
                .statusCode(200)
                .body("identity.table_name", equalTo("kayaks"))
                .body("query", notNullValue())
                .body("ui", notNullValue());

        given()
                .when()
                .get("/api/datalist/metadata/kayaks/fielddefinitions")
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0))
                .body("[0].name", notNullValue())
                .body("[0].query", notNullValue())
                .body("[0].ui", notNullValue());

        given()
                .when()
                .get("/api/datalist/metadata/kayaks/ui")
                .then()
                .statusCode(200)
                .body("tableName", equalTo("kayaks"));
    }

    @Test
    @Order(5)
    void metadatasListReflectsImport() {
        given()
                .queryParam("_limit", 1000)
                .when()
                .get("/api/metadatas")
                .then()
                .statusCode(200)
                .header("x-total-count", notNullValue())
                .body("size()", greaterThanOrEqualTo(Math.min(exportedCount, 10)));
    }
}
