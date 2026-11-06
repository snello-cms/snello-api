package io.snello.test.metadata;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.snello.model.Metadata;
import io.snello.service.MetadataService;
import io.snello.service.ai.tools.SnelloCmsTools;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ai_profile end to end: REST write (object form) → JSON column → catalog → AI tools honour tool_visible.
 */
@QuarkusTest
class MetadataAiProfileRestTest {

    @Inject
    MetadataService metadataService;

    @Inject
    SnelloCmsTools tools;

    @Test
    void aiProfileIsStoredAndHiddenEntitiesDisappearFromTools() throws Exception {
        String visible = "ai_visible_" + System.nanoTime();
        String hidden = "ai_hidden_" + System.nanoTime();
        String visibleUuid = create(visible, Map.of("summary", "test entity", "synonyms", List.of("demo thing")));
        String hiddenUuid = create(hidden, Map.of("tool_visible", false));
        try {
            Map<String, Object> stored = given().accept(ContentType.JSON)
                    .when().get("/api/metadatas/{uuid}", visibleUuid)
                    .then().statusCode(200)
                    .extract().jsonPath().getMap("");
            assertInstanceOf(String.class, stored.get("ai_profile"), "ai_profile must be persisted as JSON text");
            Metadata metadata = new Metadata(stored);
            assertEquals(List.of("demo thing"), metadata.aiFacet().synonyms);

            metadataService.refreshIfChanged();
            String entities = tools.listEntities();
            assertTrue(entities.contains(visible), entities);
            assertTrue(entities.contains("demo thing"), "synonyms should be shown to the model: " + entities);
            assertFalse(entities.contains(hidden), "tool_visible=false must hide the entity: " + entities);
            assertTrue(tools.getEntitySchema(hidden).contains("not found"));
        } finally {
            delete(visibleUuid);
            delete(hiddenUuid);
        }
    }

    @Test
    void invalidAiProfileIsRejected() {
        given().contentType(ContentType.JSON)
                .body(Map.of("table_name", "ai_invalid_" + System.nanoTime(), "table_key", "uuid",
                        "description", "x", "ai_profile", "{broken"))
                .when().post("/api/metadatas")
                .then().statusCode(400);
    }

    private static String create(String tableName, Map<String, Object> aiProfile) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("table_name", tableName, "table_key", "uuid", "table_key_type", "uuid",
                        "description", "AI profile test " + tableName, "ai_profile", aiProfile))
                .when().post("/api/metadatas")
                .then().statusCode(200)
                .extract().jsonPath().getString("uuid");
    }

    private static void delete(String uuid) {
        if (uuid != null) {
            given().when().delete("/api/metadatas/{uuid}", uuid);
        }
    }
}
