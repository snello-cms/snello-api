package io.snello.test.metadata;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.snello.model.pojo.DraftResult;
import io.snello.model.pojo.EntityDraft;
import io.snello.service.DraftService;
import io.snello.service.MetadataService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class DraftServiceTest {

    @Inject
    DraftService draftService;

    @Inject
    MetadataService metadataService;

    @Inject
    JsonMapper json;

    private EntityDraft draft(String jsonText) {
        return json.readValue(jsonText, EntityDraft.class);
    }

    @Test
    void invalidDraftReportsEveryProblemAndWritesNothing() throws Exception {
        DraftResult r = draftService.validate(draft("""
                {"table_name":"Bad-Name",
                 "fields":[{"name":"uuid","ai":{"semantic_type":"text"}},
                           {"name":"kind","ai":{"semantic_type":"enum"}},
                           {"name":"owner","ai":{"semantic_type":"reference"},"query":{"join_table_name":"no_such_entity"}},
                           {"name":"weird","ai":{"semantic_type":"colour"}}]}
                """));
        assertFalse(r.valid());
        String errors = String.join("\n", r.errors());
        assertTrue(errors.contains("table_name 'Bad-Name' must match"), errors);
        assertTrue(errors.contains("ai.description is required"), errors);
        assertTrue(errors.contains("'uuid' is the generated uuid key column"), errors);
        assertTrue(errors.contains("enum/select needs ui.options"), errors);
        assertTrue(errors.contains("'no_such_entity' is not an existing entity"), errors);
        assertTrue(errors.contains("unknown semantic_type 'colour'"), errors);
        assertTrue(r.ddl().isEmpty());
        assertFalse(r.applied());
    }

    @Test
    void reservedAndExistingNamesAreRejected() throws Exception {
        assertTrue(draftService.validate(draft("""
                {"table_name":"drafts","ai":{"description":"x"},"fields":[{"name":"a","ai":{"semantic_type":"text"}}]}
                """)).errors().stream().anyMatch(e -> e.contains("reserved")));
        assertTrue(draftService.validate(draft("""
                {"table_name":"lovers","ai":{"description":"x"},"fields":[{"name":"a","ai":{"semantic_type":"text"}}]}
                """)).errors().stream().anyMatch(e -> e.contains("already exists")));
    }

    @Test
    void validDraftDerivesUiFacetAndDdl() throws Exception {
        DraftResult r = draftService.validate(draft("""
                {"table_name":"draft_products_preview",
                 "ai":{"description":"Products sold in the shop","synonyms":["items"]},
                 "fields":[{"name":"title","ai":{"description":"Product name","semantic_type":"text"},"query":{"searchable":true}},
                           {"name":"unit_price","ai":{"semantic_type":"money"}},
                           {"name":"stock","query":{"sql_type":"integer","searchable":true}},
                           {"name":"notes","ai":{"semantic_type":"long_text"}}]}
                """));
        assertTrue(r.valid(), String.join("\n", r.errors()));
        Map<String, Map<String, Object>> byName = new java.util.HashMap<>();
        r.fields().forEach(f -> byName.put((String) f.get("name"), f));

        assertEquals("input", byName.get("title").get("type"));
        assertEquals("text", byName.get("title").get("input_type"));
        assertEquals("title_contains", byName.get("title").get("search_field_name"));
        assertEquals("decimal", byName.get("unit_price").get("input_type"));
        assertEquals("Unit price", byName.get("unit_price").get("label"));
        assertEquals("number", byName.get("stock").get("input_type"), "semantic derived from sql_type");
        assertEquals("=", byName.get("stock").get("search_condition"));
        assertEquals("textarea", byName.get("notes").get("type"));
        assertEquals(false, byName.get("notes").get("show_in_list"));
        assertFalse(byName.get("title").containsKey("required"), "rows must only carry real columns");

        assertEquals("title,stock", r.metadata().get("search_fields"));
        assertEquals("uuid", r.metadata().get("table_key"));
        assertTrue(((String) r.metadata().get("ai_profile")).contains("items"));

        String ddl = r.ddl().getFirst();
        assertTrue(ddl.startsWith("CREATE TABLE"), ddl);
        assertTrue(ddl.contains("VARCHAR(50) NOT NULL"), "uuid key column: " + ddl);
        assertTrue(ddl.contains("DOUBLE PRECISION"), "money -> decimal column: " + ddl);
        assertTrue(ddl.contains("NUMERIC(10)"), "integer column: " + ddl);
        assertTrue(ddl.contains("text default null"), "long_text column: " + ddl);
    }

    @Test
    @TestSecurity(user = "draft-admin", roles = {"Admin"})
    void applyCreatesMetadataFieldsAndTable() throws Exception {
        String table = "draft_e2e_" + System.nanoTime();
        Map<String, Object> body = Map.of(
                "table_name", table,
                "ai", Map.of("description", "End-to-end draft test"),
                "fields", List.of(
                        Map.of("name", "title", "ai", Map.of("semantic_type", "text")),
                        Map.of("name", "qty", "ai", Map.of("semantic_type", "integer"))));
        Map<String, Object> result = given().contentType(ContentType.JSON).body(body)
                .when().post("/api/drafts/apply?create_table=true")
                .then().statusCode(200)
                .extract().jsonPath().getMap("");
        assertEquals(true, result.get("applied"));
        assertEquals(true, result.get("table_created"));
        @SuppressWarnings("unchecked")
        String metadataUuid = (String) ((Map<String, Object>) result.get("metadata")).get("uuid");
        try {
            metadataService.refreshIfChanged();
            assertNotNull(metadataService.metadata(table), "metadata must be in the catalog");
            assertEquals(2, metadataService.fielddefinitions(table).size());
            // the table really exists and accepts typed data
            given().contentType(ContentType.JSON).body(Map.of("title", "first", "qty", 3))
                    .when().post("/api/" + table)
                    .then().statusCode(200);
            given().accept(ContentType.JSON)
                    .when().get("/api/" + table)
                    .then().statusCode(200).header("X-Total-Count", "1");
        } finally {
            given().when().get("/api/metadatas/{uuid}/delete", metadataUuid);
            for (var fd : metadataService.fielddefinitions(table)) {
                given().when().delete("/api/fielddefinitions/{uuid}", fd.uuid);
            }
            given().when().delete("/api/metadatas/{uuid}", metadataUuid);
        }
    }

    @Test
    @TestSecurity(user = "plain-user", roles = {"User"})
    void applyRequiresAdminOrManager() {
        given().contentType(ContentType.JSON)
                .body(Map.of("table_name", "draft_forbidden", "ai", Map.of("description", "x"),
                        "fields", List.of(Map.of("name", "a", "ai", Map.of("semantic_type", "text")))))
                .when().post("/api/drafts/apply")
                .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "draft-admin", roles = {"Admin"})
    void applyOfInvalidDraftIs400WithTheErrors() {
        given().contentType(ContentType.JSON)
                .body(Map.of("table_name", "draft_invalid", "fields", List.of()))
                .when().post("/api/drafts/apply")
                .then().statusCode(400)
                .extract().jsonPath().getList("errors").contains("at least one field is required");
    }
}
