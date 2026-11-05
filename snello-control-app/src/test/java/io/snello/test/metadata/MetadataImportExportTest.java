package io.snello.test.metadata;

import io.quarkus.test.junit.QuarkusTest;
import tools.jackson.databind.json.JsonMapper;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MetadataImportExportTest {

    private static final String TABLE_NAME = "import_export_test_table";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    // ── helpers ──────────────────────────────────────────────────────────────

    private String seedMetadataAndGetUuid() {
        // cleanup se esiste già
        deleteByTableName(TABLE_NAME);

        Map<String, Object> metadataPayload = Map.of(
                "table_name", TABLE_NAME,
                "table_key", "uuid",
                "table_key_type", "uuid",
                "description", "Test metadata for import/export",
                "api_protected", false,
                "already_exist", false,
                "created", false
        );

        Map<String, Object> created =
                given()
                        .contentType(ContentType.JSON)
                        .body(metadataPayload)
                        .when()
                        .post("/api/metadatas")
                        .then()
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getMap("$");

        String uuid = (String) created.get("uuid");
        assertNotNull(uuid, "metadata uuid deve essere valorizzato");

        // aggiungi un field definition
        Map<String, Object> fieldPayload = Map.of(
                "metadata_uuid", uuid,
                "metadata_name", TABLE_NAME,
                "name", "title",
                "label", "Title",
                "type", "input",
                "input_type", "text",
                "searchable", true,
                "view_index", 1
        );

        given()
                .contentType(ContentType.JSON)
                .body(fieldPayload)
                .when()
                .post("/api/fielddefinitions")
                .then()
                .statusCode(200);

        return uuid;
    }

    private void deleteByTableName(String tableName) {
        @SuppressWarnings("unchecked")
        List<Map> existing =
                given()
                        .accept(ContentType.JSON)
                        .queryParam("table_name", tableName)
                        .queryParam("_limit", 100)
                        .when()
                        .get("/api/metadatas")
                        .then()
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getList("", Map.class);

        for (Map meta : existing) {
            Object id = meta.get("uuid");
            if (id != null) {
                given()
                        .when()
                        .delete("/api/metadatas/{id}", id)
                        .then()
                        .statusCode(200);
            }
        }
    }

    // ── test export ──────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void exportShouldReturnJsonFileWithMetadataAndFields() {
        String uuid = seedMetadataAndGetUuid();

        Map<String, Object> exportBody = Map.of("metadatas", List.of(uuid));

        byte[] responseBytes =
                given()
                        .contentType(ContentType.JSON)
                        .body(exportBody)
                        .when()
                        .post("/api/metadatas/export")
                        .then()
                        .statusCode(200)
                        .header("Content-Disposition", notNullValue())
                        .extract()
                        .asByteArray();

        assertNotNull(responseBytes);
        assertTrue(responseBytes.length > 0, "Il file esportato non deve essere vuoto");

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = MAPPER.readValue(responseBytes, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> metadatas = (List<Map<String, Object>>) parsed.get("metadatas");
            assertNotNull(metadatas, "La chiave 'metadatas' deve essere presente");
            assertFalse(metadatas.isEmpty(), "La lista metadatas non deve essere vuota");

            Map<String, Object> entry = metadatas.get(0);
            assertNotNull(entry.get("metadata"), "Ogni entry deve contenere 'metadata'");
            assertNotNull(entry.get("fields"), "Ogni entry deve contenere 'fields'");

            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) entry.get("metadata");
            assertEquals(TABLE_NAME, meta.get("table_name"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> fields = (List<Map<String, Object>>) entry.get("fields");
            assertFalse(fields.isEmpty(), "Deve esserci almeno una field definition");
            assertEquals("title", fields.get(0).get("name"));
        } catch (Exception e) {
            fail("Impossibile deserializzare la risposta: " + e.getMessage());
        }

        // cleanup
        deleteByTableName(TABLE_NAME);
    }

    @Test
    @Order(2)
    void exportWithUnknownUuidShouldReturnEmptyList() {
        Map<String, Object> exportBody = Map.of("metadatas", List.of("00000000-0000-0000-0000-000000000000"));

        given()
                .contentType(ContentType.JSON)
                .body(exportBody)
                .when()
                .post("/api/metadatas/export")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("metadatas")
                .isEmpty();
    }

    // ── test import ──────────────────────────────────────────────────────────

    @Test
    @Order(3)
    void importShouldSaveMetadataAndFields() throws Exception {
        deleteByTableName(TABLE_NAME);

        // costruisci il payload identico all'output dell'export
        Map<String, Object> metaMap = Map.of(
                "table_name", TABLE_NAME,
                "table_key", "uuid",
                "table_key_type", "uuid",
                "description", "Importato via test",
                "api_protected", false,
                "already_exist", false,
                "created", false
        );
        Map<String, Object> fieldMap = Map.of(
                "name", "title",
                "label", "Title",
                "type", "input",
                "input_type", "text",
                "metadata_name", TABLE_NAME,
                "searchable", true,
                "view_index", 1
        );
        Map<String, Object> entry = Map.of("metadata", metaMap, "fields", List.of(fieldMap));
        Map<String, Object> importBody = Map.of("metadatas", List.of(entry));

        byte[] jsonBytes = MAPPER.writeValueAsBytes(importBody);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> imported = (List<Map<String, Object>>) (List<?>)
                given()
                        .multiPart("file", "export.json", jsonBytes, "application/octet-stream")
                        .when()
                        .post("/api/metadatas/import")
                        .then()
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getList("imported", Map.class);

        assertNotNull(imported);
        assertFalse(imported.isEmpty());
        assertEquals(TABLE_NAME, imported.get(0).get("table_name"));

        // cleanup
        deleteByTableName(TABLE_NAME);
    }

    @Test
    @Order(4)
    void importShouldReturnConflictIfMetadataAlreadyExists() throws Exception {
        String uuid = seedMetadataAndGetUuid();

        // recupera il metadata per usarlo nel payload
        @SuppressWarnings("unchecked")
        Map<String, Object> existingMeta =
                given()
                        .accept(ContentType.JSON)
                        .when()
                        .get("/api/metadatas/{id}", uuid)
                        .then()
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getMap("$");

        Map<String, Object> entry = Map.of("metadata", existingMeta, "fields", List.of());
        Map<String, Object> importBody = Map.of("metadatas", List.of(entry));
        byte[] jsonBytes = MAPPER.writeValueAsBytes(importBody);

        given()
                .multiPart("file", "export.json", jsonBytes, "application/octet-stream")
                .when()
                .post("/api/metadatas/import")
                .then()
                .statusCode(409);

        // cleanup
        deleteByTableName(TABLE_NAME);
    }
}
