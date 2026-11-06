package io.snello.test.query;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.snello.model.FieldDefinition;
import io.snello.model.Metadata;
import io.snello.service.EntityCatalog;
import io.snello.service.MetadataService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The query image must expose only reads on declared resources: no data writes, no admin RS, no system tables.
 */
@QuarkusTest
class QueryPlaneSurfaceTest {

    @Inject
    MetadataService metadataService;

    @Inject
    EntityCatalog catalog;

    @Test
    void dataWritesAreNotRouted() {
        given().contentType(ContentType.JSON).body(Map.of("name", "x"))
                .when().post("/api/lovers")
                .then().statusCode(405);
        given().contentType(ContentType.JSON).body(Map.of("name", "x"))
                .when().put("/api/lovers/some-id")
                .then().statusCode(405);
        given()
                .when().delete("/api/lovers/some-id")
                .then().statusCode(405);
    }

    @Test
    void systemTablesAreNotServedThroughGenericApi() {
        // on control these paths hit MetadataServiceRs / FieldDefinitionsServiceRs; here the generic reader
        // must refuse them because they are not declared metadata entities
        for (String path : new String[]{"/api/metadatas", "/api/fielddefinitions", "/api/actions", "/api/ai_tools"}) {
            given().accept(ContentType.JSON)
                    .when().get(path)
                    .then().statusCode(404);
        }
    }

    @Test
    void adminResourcesAreAbsent() {
        given().contentType(ContentType.JSON).body(Map.of())
                .when().post("/api/metadatas")
                .then().statusCode(405);
        given().accept(ContentType.JSON)
                .when().get("/api/datalist/names")
                .then().statusCode(404);
    }

    @Test
    void onlyTheQueryFacetIsLoaded() throws Exception {
        assertFalse(metadataService.metadataMap().isEmpty(), "seed has metadata");
        int fields = 0;
        for (Metadata m : metadataService.metadataMap().values()) {
            assertNotNull(m.table_name);
            assertNotNull(m.table_key, "query facet column loaded");
            assertNull(m.icon, "UI facet must not be loaded on the query plane: " + m.table_name);
            assertNull(m.description, "AI facet must not be loaded on the query plane: " + m.table_name);
            assertNull(m.ai_profile, "AI facet must not be loaded on the query plane: " + m.table_name);
            for (FieldDefinition fd : metadataService.fielddefinitions(m.table_name)) {
                fields++;
                assertNotNull(fd.name);
                assertNull(fd.label, "UI facet must not be loaded: " + m.table_name + "." + fd.name);
                assertNull(fd.type, "UI facet must not be loaded: " + m.table_name + "." + fd.name);
            }
        }
        assertTrue(fields > 0, "seed has field definitions");
        assertTrue(metadataService.actionsMap().isEmpty(), "actions are write-path only");
        assertTrue(metadataService.aiToolsMap().isEmpty(), "AI tools are not shipped on the query plane");
        assertEquals(metadataService.metadataMap().size(), catalog.queryViews().size(), "one view per entity");
    }

    @Test
    void aiRuntimeIsNotShipped() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("io.snello.service.ai.tools.SnelloCmsTools"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("dev.langchain4j.model.chat.ChatModel"));
    }

    @Test
    void metadataSnapshotIsVersioned() throws Exception {
        metadataService.refreshIfChanged();
        String version = metadataService.version();
        assertNotNull(version);
        assertFalse(metadataService.refreshIfChanged(), "unchanged tables must not publish a new snapshot");
        assertEquals(version, metadataService.version());
    }
}
