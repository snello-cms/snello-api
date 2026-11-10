package io.snello.test.metadata;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Metadata export/import carrying table rows: {@code "data": true}, {@code "format": "json" | "zip"}.
 * Round trips drop metadata + table, then import must recreate the table and the rows.
 */
@QuarkusTest
@TestSecurity(user = "test-admin", roles = {"Admin"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MetadataDataTransferTest {

    private static final String TABLE = "metadata_data_transfer_it";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static String metadataUuid;
    private static byte[] exportedZip;
    private static Map<String, Object> exportedJson;

    @Test
    @Order(1)
    void seed() {
        cleanup();
        Map<String, Object> meta = new HashMap<>();
        meta.put("table_name", TABLE);
        meta.put("table_key", "uuid");
        meta.put("table_key_type", "uuid");
        meta.put("description", "metadata + data transfer IT");
        meta.put("api_protected", false);
        meta.put("already_exist", false);
        meta.put("created", false);
        metadataUuid = given().contentType(ContentType.JSON).body(meta)
                .when().post("/api/metadatas").then().statusCode(200).extract().path("uuid");
        assertNotNull(metadataUuid);

        given().contentType(ContentType.JSON).body(Map.of(
                "metadata_uuid", metadataUuid, "metadata_name", TABLE, "name", "title", "label", "Title",
                "type", "input", "input_type", "text", "show_in_list", true, "view_index", 1
        )).when().post("/api/fielddefinitions").then().statusCode(200);
        given().when().post("/api/metadatas/{uuid}/create", metadataUuid).then().statusCode(200);

        for (String[] r : new String[][]{{"row-1", "Alpha"}, {"row-2", "Beta"}, {"row-3", "Gamma"}}) {
            given().contentType(ContentType.JSON).body(Map.of("uuid", r[0], "title", r[1]))
                    .when().post("/api/" + TABLE).then().statusCode(200);
        }
        assertRowCount(3);
    }

    @Test
    @Order(2)
    void exportWithoutDataIsUnchanged() {
        Map<String, Object> entry = given().contentType(ContentType.JSON)
                .body(Map.of("metadatas", List.of(metadataUuid)))
                .when().post("/api/metadatas/export").then().statusCode(200)
                .extract().jsonPath().getMap("metadatas[0]");
        assertNotNull(entry.get("metadata"));
        assertNotNull(entry.get("fields"));
        assertFalse(entry.containsKey("data"), "no data unless asked");
    }

    @Test
    @Order(3)
    void exportJsonWithData() {
        exportedJson = given().contentType(ContentType.JSON)
                .body(Map.of("metadatas", List.of(metadataUuid), "data", true))
                .when().post("/api/metadatas/export").then().statusCode(200)
                .extract().jsonPath().getMap("$");
        @SuppressWarnings("unchecked")
        Map<String, Object> entry = ((List<Map<String, Object>>) exportedJson.get("metadatas")).get(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) entry.get("data");
        assertEquals(3, rows.size());
        assertTrue(rows.stream().anyMatch(r -> "Beta".equals(r.get("title"))));
    }

    @Test
    @Order(4)
    void exportZipWithData() throws Exception {
        exportedZip = given().contentType(ContentType.JSON)
                .body(Map.of("metadatas", List.of(metadataUuid), "data", true, "format", "zip"))
                .when().post("/api/metadatas/export").then().statusCode(200)
                .contentType(containsString("application/zip"))
                .header("Content-Disposition", containsString("metadatas-export.zip"))
                .extract().asByteArray();

        Map<String, byte[]> zip = readZip(exportedZip);
        assertTrue(zip.containsKey("manifest.json"), zip.keySet().toString());
        assertTrue(zip.containsKey("metadatas.json"), zip.keySet().toString());
        assertTrue(zip.containsKey("data/" + TABLE + "/0001.json"), zip.keySet().toString());

        @SuppressWarnings("unchecked")
        Map<String, Object> manifest = MAPPER.readValue(zip.get("manifest.json"), Map.class);
        assertEquals("snello-metadata-export", manifest.get("kind"));
        assertEquals(true, manifest.get("data"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = MAPPER.readValue(zip.get("data/" + TABLE + "/0001.json"), List.class);
        assertEquals(3, rows.size());
    }

    @Test
    @Order(5)
    void importZipRecreatesTableAndRows() {
        assertNotNull(exportedZip);
        cleanup();

        given().multiPart("file", "metadatas-export.zip", exportedZip, "application/zip")
                .when().post("/api/metadatas/import").then().statusCode(200)
                .body("imported.size()", equalTo(1))
                .body("data.imported", equalTo(3))
                .body("data.skipped", equalTo(0))
                .body("data.errors.size()", equalTo(0))
                .body("data.tables_created", equalTo(List.of(TABLE)));
        assertRowCount(3);
    }

    @Test
    @Order(6)
    void importJsonWithDataRecreatesTableAndRows() {
        assertNotNull(exportedJson);
        cleanup();

        given().contentType(ContentType.JSON).body(exportedJson)
                .when().post("/api/metadatas/import").then().statusCode(200)
                .body("data.imported", equalTo(3))
                .body("data.tables_created", equalTo(List.of(TABLE)));
        assertRowCount(3);
    }

    @Test
    @Order(7)
    void importJsonFileUploadStillWorks() throws Exception {
        cleanup();
        byte[] json = MAPPER.writeValueAsBytes(exportedJson);
        given().multiPart("file", "metadatas-export.json", json, "application/json")
                .when().post("/api/metadatas/import").then().statusCode(200)
                .body("data.imported", equalTo(3));
        assertRowCount(3);
    }

    @Test
    @Order(8)
    void importRawZipBody() {
        cleanup();
        given().contentType("application/zip").body(exportedZip)
                .when().post("/api/metadatas/import").then().statusCode(200)
                .body("data.imported", equalTo(3));
        assertRowCount(3);
    }

    @Test
    @Order(9)
    void importWithoutDataReturnsNoDataSection() {
        cleanup();
        @SuppressWarnings("unchecked")
        Map<String, Object> entry = new HashMap<>(((List<Map<String, Object>>) exportedJson.get("metadatas")).get(0));
        entry.remove("data");
        given().contentType(ContentType.JSON).body(Map.of("metadatas", List.of(entry)))
                .when().post("/api/metadatas/import").then().statusCode(200)
                .body("imported.size()", equalTo(1))
                .body("data", org.hamcrest.Matchers.nullValue());
    }

    @Test
    @Order(10)
    void backupZipIsRejectedWithHint() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write("{\"version\":1,\"kind\":\"snello-backup-data\"}".getBytes());
            zos.closeEntry();
        }
        given().multiPart("file", "backup.zip", bos.toByteArray(), "application/zip")
                .when().post("/api/metadatas/import").then().statusCode(400)
                .body(containsString("/api/backup/data"));
    }

    @Test
    @Order(11)
    void invalidFormatIs400() {
        given().contentType(ContentType.JSON)
                .body(Map.of("metadatas", List.of(metadataUuid == null ? "x" : metadataUuid), "format", "xml"))
                .when().post("/api/metadatas/export").then().statusCode(400);
    }

    @Test
    @Order(12)
    void cleanupSeed() {
        cleanup();
    }

    private void assertRowCount(int n) {
        given().queryParam("_limit", 50).when().get("/api/" + TABLE)
                .then().statusCode(200).body("size()", equalTo(n));
    }

    private void cleanup() {
        List<Map> existing = given().accept(ContentType.JSON)
                .queryParam("table_name", TABLE).queryParam("_limit", 100)
                .when().get("/api/metadatas").then().statusCode(200)
                .extract().jsonPath().getList("", Map.class);
        for (Map meta : existing) {
            Object uuid = meta.get("uuid");
            if (uuid == null) {
                continue;
            }
            given().when().post("/api/metadatas/{uuid}/delete", uuid); // drop the physical table
            given().when().delete("/api/metadatas/{id}", uuid).then().statusCode(200);
        }
    }

    private static Map<String, byte[]> readZip(byte[] bytes) throws Exception {
        Map<String, byte[]> out = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (!e.isDirectory()) {
                    out.put(e.getName(), zis.readAllBytes());
                }
            }
        }
        return out;
    }
}
