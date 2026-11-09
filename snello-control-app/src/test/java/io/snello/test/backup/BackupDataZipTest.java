package io.snello.test.backup;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
// configuration writes (metadatas, fielddefinitions, import, table DDL) are Admin only
@TestSecurity(user = "test-admin", roles = {"Admin"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BackupDataZipTest {

    private static final String TABLE = "backup_data_it";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static String metadataUuid;
    private static byte[] exportedZip;

    @Test
    @Order(1)
    void seedMetadataTableAndRows() {
        cleanup();

        Map<String, Object> metadataPayload = new HashMap<>();
        metadataPayload.put("table_name", TABLE);
        metadataPayload.put("table_key", "uuid");
        metadataPayload.put("table_key_type", "uuid");
        metadataPayload.put("description", "Backup data zip IT");
        metadataPayload.put("api_protected", false);
        metadataPayload.put("already_exist", false);
        metadataPayload.put("created", false);

        Map<String, Object> created = given()
                .contentType(ContentType.JSON)
                .body(metadataPayload)
                .when()
                .post("/api/metadatas")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");

        metadataUuid = (String) created.get("uuid");
        assertNotNull(metadataUuid);

        // uuid key column comes from metadata.table_key; only add non-key fields
        given().contentType(ContentType.JSON).body(Map.of(
                "metadata_uuid", metadataUuid,
                "metadata_name", TABLE,
                "name", "title",
                "label", "Title",
                "type", "input",
                "input_type", "text",
                "show_in_list", true,
                "view_index", 1
        )).when().post("/api/fielddefinitions").then().statusCode(200);

        given().when().post("/api/metadatas/{uuid}/create", metadataUuid).then().statusCode(200);

        given().contentType(ContentType.JSON)
                .body(Map.of("uuid", "row-1", "title", "Alpha"))
                .when().post("/api/" + TABLE).then().statusCode(200);
        given().contentType(ContentType.JSON)
                .body(Map.of("uuid", "row-2", "title", "Beta"))
                .when().post("/api/" + TABLE).then().statusCode(200);

        given().queryParam("_limit", 10)
                .when().get("/api/" + TABLE)
                .then().statusCode(200)
                .body("size()", equalTo(2));
    }

    @Test
    @Order(2)
    void exportSingleTableZipHasManifestAndChunk() throws Exception {
        exportedZip = given()
                .when()
                .get("/api/backup/data/{name}", TABLE)
                .then()
                .statusCode(200)
                .header("Content-Disposition", org.hamcrest.Matchers.containsString("snello-backup-data-" + TABLE))
                .extract()
                .asByteArray();

        assertTrue(exportedZip.length > 0);

        Map<String, byte[]> entries = readZip(exportedZip);
        assertTrue(entries.containsKey("manifest.json"));
        assertTrue(entries.containsKey("data/" + TABLE + "/0001.json"));

        @SuppressWarnings("unchecked")
        Map<String, Object> manifest = MAPPER.readValue(entries.get("manifest.json"), Map.class);
        assertEquals("snello-backup-data", manifest.get("kind"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tables = (List<Map<String, Object>>) manifest.get("tables");
        assertEquals(1, tables.size());
        assertEquals(TABLE, tables.get(0).get("name"));
        assertEquals(2, ((Number) tables.get(0).get("rows")).intValue());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = MAPPER.readValue(entries.get("data/" + TABLE + "/0001.json"), List.class);
        assertEquals(2, rows.size());
    }

    @Test
    @Order(3)
    void importAfterTruncateRestoresRows() {
        assertNotNull(exportedZip);
        assertNotNull(metadataUuid);

        given().when().post("/api/metadatas/{uuid}/truncate", metadataUuid).then().statusCode(200);

        given().queryParam("_limit", 10)
                .when().get("/api/" + TABLE)
                .then().statusCode(200)
                .body("size()", equalTo(0));

        Map result = given()
                .multiPart("file", "snello-backup-data-" + TABLE + ".zip", exportedZip, "application/zip")
                .when()
                .post("/api/backup/data")
                .then()
                .statusCode(200)
                .body("imported", equalTo(2))
                .body("skipped", equalTo(0))
                .extract()
                .jsonPath()
                .getMap("$");

        assertNotNull(result);

        given().queryParam("_limit", 10)
                .when().get("/api/" + TABLE)
                .then().statusCode(200)
                .body("size()", equalTo(2));

        // idempotent re-import
        given()
                .multiPart("file", "again.zip", exportedZip, "application/zip")
                .when()
                .post("/api/backup/data")
                .then()
                .statusCode(200)
                .body("imported", equalTo(0))
                .body("skipped", greaterThanOrEqualTo(2));
    }

    @Test
    @Order(4)
    void exportAllIncludesTable() throws Exception {
        byte[] all = given()
                .when()
                .get("/api/backup/data")
                .then()
                .statusCode(200)
                .extract()
                .asByteArray();

        Map<String, byte[]> entries = readZip(all);
        assertTrue(entries.containsKey("manifest.json"));
        assertTrue(entries.containsKey("data/" + TABLE + "/0001.json"));
    }

    @Test
    @Order(5)
    void unknownTableExportIs404() {
        given().when().get("/api/backup/data/{name}", "no_such_backup_table_xyz")
                .then().statusCode(404);
    }

    @Test
    @Order(6)
    void cleanupSeed() {
        cleanup();
    }

    private void cleanup() {
        List<Map> existing = given()
                .accept(ContentType.JSON)
                .queryParam("table_name", TABLE)
                .queryParam("_limit", 100)
                .when()
                .get("/api/metadatas")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("", Map.class);

        for (Map meta : existing) {
            Object uuid = meta.get("uuid");
            if (uuid == null) {
                continue;
            }
            // drop physical table if present
            given().when().get("/api/metadatas/{uuid}/delete", uuid);
            given().when().delete("/api/metadatas/{id}", uuid).then().statusCode(200);
        }
    }

    private static Map<String, byte[]> readZip(byte[] zipBytes) throws Exception {
        Map<String, byte[]> out = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                out.put(entry.getName(), zis.readAllBytes());
            }
        }
        return out;
    }
}
