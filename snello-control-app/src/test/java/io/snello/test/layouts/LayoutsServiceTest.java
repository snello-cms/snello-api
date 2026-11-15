package io.snello.test.layouts;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.snello.api.service.JdbcRepository;
import io.snello.service.MetadataService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class LayoutsServiceTest {

    private static final String META = "layout-meta-" + System.nanoTime();
    private static final String META_UUID = "layout-meta-uuid";

    @Inject
    JdbcRepository jdbc;

    @Inject
    MetadataService metadataService;

    @BeforeEach
    void seed() throws Exception {
        cleanup();
        Map<String, Object> m = new HashMap<>();
        m.put("uuid", META_UUID);
        m.put("table_name", META);
        m.put("table_key", "uuid");
        m.put("table_key_type", "uuid");
        m.put("created", false);
        m.put("already_exist", true);
        jdbc.create("metadatas", "uuid", m);
        metadataService.invalidateMetadataCaches();
    }

    @AfterEach
    void cleanup() throws Exception {
        jdbc.executeQuery("DELETE FROM layouts WHERE metadata_name = '" + META + "' OR name LIKE 'layout-test-%'");
        jdbc.executeQuery("DELETE FROM metadatas WHERE uuid = '" + META_UUID + "'");
        metadataService.invalidateMetadataCaches();
    }

    @Test
    @TestSecurity(user = "admin", roles = {"Admin"})
    void createListLayoutAndRejectKindMismatch() {
        Map<String, Object> body = Map.of(
                "name", "layout-test-list",
                "page_type", "list",
                "metadata_uuid", META_UUID,
                "json_data", Map.of("version", 1, "kind", "list", "columns", List.of()));

        Map<String, Object> created = given().contentType(ContentType.JSON).body(body)
                .when().post("/api/layouts")
                .then().statusCode(200)
                .body("name", equalTo("layout-test-list"))
                .body("page_type", equalTo("list"))
                .body("metadata_name", equalTo(META))
                .body("updated_at", notNullValue())
                .extract().jsonPath().getMap("");

        assertTrue(((String) created.get("json_data")).contains("\"kind\""));

        given().contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "layout-test-bad",
                        "page_type", "list",
                        "metadata_uuid", META_UUID,
                        "json_data", Map.of("version", 1, "kind", "wizard", "pages", List.of())))
                .when().post("/api/layouts")
                .then().statusCode(400);

        given().contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "layout-test-missing-meta",
                        "page_type", "edit",
                        "metadata_uuid", "no-such-metadata",
                        "json_data", Map.of("version", 1, "kind", "wizard", "pages", List.of())))
                .when().post("/api/layouts")
                .then().statusCode(400);

        // clone: second layout same metadata + page_type is allowed
        given().contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "layout-test-list-clone",
                        "page_type", "list",
                        "metadata_uuid", META_UUID,
                        "json_data", Map.of("version", 1, "kind", "list", "columns", List.of("a"))))
                .when().post("/api/layouts")
                .then().statusCode(200)
                .body("name", equalTo("layout-test-list-clone"));

        given().when().get("/api/layouts?metadata_name={n}&_limit=50", META)
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(2));
    }

    @Test
    @TestSecurity(user = "admin", roles = {"Admin"})
    void editAndViewRequireWizardKind() {
        given().contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "layout-test-edit",
                        "page_type", "edit",
                        "metadata_uuid", META_UUID,
                        "json_data", "{\"version\":1,\"kind\":\"wizard\",\"pages\":[]}"))
                .when().post("/api/layouts")
                .then().statusCode(200)
                .body("page_type", equalTo("edit"));

        given().contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "layout-test-view",
                        "page_type", "view",
                        "metadata_uuid", META_UUID,
                        "json_data", Map.of("version", 1, "kind", "wizard", "pages", List.of())))
                .when().post("/api/layouts")
                .then().statusCode(200)
                .body("page_type", equalTo("view"));
    }
}
