package io.snello.test.metadata;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class ProgramsMetadataCreationTest {

    @Test
    void shouldCreateProgramsMetadataAndItsFieldDefinitionsViaApi() {
        cleanupExistingProgramsMetadata();

        Map<String, Object> metadataPayload = Map.of(
                "table_name", "programs",
                "table_key", "uuid",
                "table_key_type", "uuid",
                "description", "Programs metadata for integration test",
                "api_protected", false,
                "already_exist", false,
                "created", false
        );

        Map<String, Object> createdMetadata =
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

        assertNotNull(createdMetadata);
        String metadataUuid = String.valueOf(createdMetadata.get("uuid"));
        assertNotNull(metadataUuid);
        assertEquals("programs", createdMetadata.get("table_name"));
        assertEquals("uuid", createdMetadata.get("table_key"));
        assertEquals("uuid", createdMetadata.get("table_key_type"));

        Map<String, Object> nameFieldPayload = Map.of(
                "metadata_uuid", metadataUuid,
                "metadata_name", "programs",
                "name", "name",
                "label", "Name",
                "type", "input",
                "input_type", "text",
                "show_in_list", true,
                "searchable", true,
                "view_index", 1
        );

        Map<String, Object> userOwnerFieldPayload = Map.of(
                "metadata_uuid", metadataUuid,
                "metadata_name", "programs",
                "name", "user_owner",
                "label", "User Owner",
                "type", "input",
                "input_type", "text",
                "show_in_list", true,
                "searchable", true,
                "view_index", 2
        );

        Map<String, Object> createdNameField =
                given()
                        .contentType(ContentType.JSON)
                        .body(nameFieldPayload)
                        .when()
                        .post("/api/fielddefinitions")
                        .then()
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getMap("$");

        Map<String, Object> createdUserOwnerField =
                given()
                        .contentType(ContentType.JSON)
                        .body(userOwnerFieldPayload)
                        .when()
                        .post("/api/fielddefinitions")
                        .then()
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getMap("$");

        assertEquals("name", createdNameField.get("name"));
        assertEquals("programs", createdNameField.get("metadata_name"));
        assertEquals("input", createdNameField.get("type"));

        assertEquals("user_owner", createdUserOwnerField.get("name"));
        assertEquals("programs", createdUserOwnerField.get("metadata_name"));
        assertEquals("input", createdUserOwnerField.get("type"));
    }

    private void cleanupExistingProgramsMetadata() {
        List<Map> existingPrograms =
                given()
                        .accept(ContentType.JSON)
                        .queryParam("table_name", "programs")
                        .queryParam("_limit", 100)
                        .when()
                        .get("/api/metadatas")
                        .then()
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                                                .getList("", Map.class);

                for (Map metadata : existingPrograms) {
            Object uuid = metadata.get("uuid");
            if (uuid != null) {
                given()
                        .when()
                        .delete("/api/metadatas/{id}", uuid)
                        .then()
                        .statusCode(200);
            }
        }
    }
}
