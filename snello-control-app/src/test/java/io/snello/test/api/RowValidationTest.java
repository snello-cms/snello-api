package io.snello.test.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.snello.api.service.JdbcRepository;
import io.snello.service.MetadataService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regressions from the end-to-end review: the API accepted rows without mandatory fields and select values outside
 * the options, and answered bad input (invalid date, missing slug source) with 500 and raw SQL. Also: an imported
 * metadata kept created=true although its table did not exist.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestSecurity(user = "rv-admin", roles = {"Admin"})
class RowValidationTest {

    private static final String TABLE = "rv_boats";
    private static final String META = "rv-boats-meta";

    @Inject
    JdbcRepository jdbc;

    @Inject
    MetadataService metadataService;

    @BeforeAll
    void seed() throws Exception {
        cleanup();
        jdbc.executeQuery("CREATE TABLE " + TABLE + " (uuid varchar(50) PRIMARY KEY, name varchar(50), hull varchar(20), launched date)");
        Map<String, Object> m = new HashMap<>();
        m.put("uuid", META);
        m.put("table_name", TABLE);
        m.put("table_key", "uuid");
        m.put("table_key_type", "slug");
        m.put("table_key_addition", "name");
        m.put("created", true);
        jdbc.create("metadatas", "uuid", m);
        field("name", "input", null, true, 1);
        field("hull", "select", "kayak,surfski,canoe", false, 2);
        field("launched", "date", null, false, 3);
        metadataService.invalidateMetadataCaches();
    }

    private void field(String name, String type, String options, boolean mandatory, int index) throws Exception {
        Map<String, Object> f = new HashMap<>();
        f.put("uuid", "rv-" + name);
        f.put("metadata_uuid", META);
        f.put("metadata_name", TABLE);
        f.put("name", name);
        f.put("label", name);
        f.put("type", type);
        f.put("options", options);
        f.put("mandatory", mandatory);
        f.put("view_index", index);
        jdbc.create("fielddefinitions", "uuid", f);
    }

    @AfterAll
    void cleanup() throws Exception {
        jdbc.executeQuery("DROP TABLE IF EXISTS " + TABLE);
        jdbc.executeQuery("DELETE FROM metadatas WHERE uuid = '" + META + "' OR table_name = 'rv_imported_missing'");
        jdbc.executeQuery("DELETE FROM fielddefinitions WHERE metadata_name IN ('" + TABLE + "', 'rv_imported_missing')");
        metadataService.invalidateMetadataCaches();
    }

    private io.restassured.response.ValidatableResponse post(Map<String, Object> body) {
        return given().contentType(ContentType.JSON).body(body).when().post("/api/" + TABLE).then();
    }

    @Test
    void mandatoryAndOptionsAreEnforced() {
        post(Map.of("hull", "kayak")).statusCode(400).body(containsString("name is required"));
        post(Map.of("name", "Sub", "hull", "submarine")).statusCode(400).body(containsString("hull must be one of"));
        post(Map.of("name", "Ok", "hull", "kayak")).statusCode(200);
        // partial update may omit a mandatory field, but not blank it
        given().contentType(ContentType.JSON).body(Map.of("hull", "canoe")).when().put("/api/" + TABLE + "/ok").then().statusCode(200);
        given().contentType(ContentType.JSON).body(Map.of("name", " ")).when().put("/api/" + TABLE + "/ok").then().statusCode(400);
    }

    @Test
    void badValuesAreClientErrors() {
        post(Map.of("name", "Bad Date", "launched", "not-a-date")).statusCode(400);
    }

    @Test
    void importedMetadataIsCreatedOnlyIfTheTableExists() throws Exception {
        Map<String, Object> meta = Map.of("table_name", "rv_imported_missing", "table_key", "uuid",
                "table_key_type", "uuid", "created", true);
        given().contentType(ContentType.JSON).body(Map.of("metadatas", List.of(Map.of("metadata", meta, "fields", List.of()))))
                .when().post("/api/metadatas/import").then().statusCode(200);
        Object created = jdbc.list("SELECT created FROM metadatas WHERE table_name = 'rv_imported_missing'").get(0).get("created");
        assertEquals(Boolean.FALSE, created);
    }
}
