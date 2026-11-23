package io.snello.test.openapi;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAPI of the data API generated from the metadata: schema types, per-type filters, security per table, and
 * every documented list filter actually accepted by GET /api/{table} (the spec must not promise what the server
 * refuses).
 */
@QuarkusTest
@TestSecurity(user = "test-admin", roles = {"Admin"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataOpenApiTest {

    private static final String CUSTOMER = "oa_customer";
    private static final String ORDER = "oa_order";

    private boolean seeded;

    /** Lazily from each test: in @BeforeAll the app (and RestAssured's port) is not up yet. */
    private synchronized void seed() {
        if (seeded) {
            return;
        }
        seeded = true;
        cleanup();
        String customer = metadata(CUSTOMER, false, null, "Customers", "Anagrafiche");
        field(customer, CUSTOMER, Map.of("name", "full_name", "label", "Full name", "type", "input", "input_type", "text",
                "mandatory", true, "view_index", 1));
        field(customer, CUSTOMER, Map.of("name", "email", "label", "Email", "type", "input", "input_type", "email", "view_index", 2));
        field(customer, CUSTOMER, Map.of("name", "birth", "label", "Birth date", "type", "date", "view_index", 3));
        field(customer, CUSTOMER, Map.of("name", "active", "label", "Active", "type", "checkbox", "view_index", 4));
        field(customer, CUSTOMER, Map.of("name", "status", "label", "Status", "type", "select", "options", "new,approved,closed",
                "view_index", 5));
        field(customer, CUSTOMER, Map.of("name", "score", "label", "Score", "type", "input", "input_type", "number", "view_index", 6));
        given().when().post("/api/metadatas/{uuid}/create", customer).then().statusCode(200);

        String order = metadata(ORDER, true, "owner", "Orders", "Acquisti");
        field(order, ORDER, Map.of("name", "title", "label", "Title", "type", "input", "input_type", "text", "view_index", 1));
        field(order, ORDER, Map.of("name", "owner", "label", "Owner", "type", "input", "input_type", "text", "view_index", 2));
        field(order, ORDER, Map.of("name", "customer_id", "label", "Customer", "type", "join", "join_table_name", CUSTOMER,
                "join_table_key", "uuid", "join_table_select_fields", "full_name", "view_index", 3));
        given().when().post("/api/metadatas/{uuid}/create", order).then().statusCode(200);

        given().contentType(ContentType.JSON).body(Map.of("uuid", "c1", "full_name", "Rossi Mario", "email", "m@x.it",
                "status", "new", "score", 5)).when().post("/api/" + CUSTOMER).then().statusCode(200);
    }

    /** Drops the tables of a previous run (Admin: called from a test, where @TestSecurity applies). */
    private void cleanup() {
        for (String t : List.of(ORDER, CUSTOMER)) {
            List<Map> existing = given().queryParam("table_name", t).queryParam("_limit", 100)
                    .when().get("/api/metadatas").then().statusCode(200).extract().jsonPath().getList("", Map.class);
            for (Map meta : existing) {
                given().when().get("/api/metadatas/{uuid}/delete", meta.get("uuid"));
                given().when().delete("/api/metadatas/{id}", meta.get("uuid")).then().statusCode(200);
            }
        }
    }

    @Test
    void oneTableDocument() {
        seed();
        String raw = given().when().get("/openapi/data/" + CUSTOMER).then().statusCode(200)
                .body("openapi", equalTo("3.0.3")).extract().asString();
        writeSample("data-openapi-" + CUSTOMER + ".json", raw);
        JsonPath doc = new JsonPath(raw);

        Map<String, Object> paths = doc.getMap("paths");
        assertTrue(paths.containsKey("/api/" + CUSTOMER), paths.keySet().toString());
        assertTrue(paths.containsKey("/api/" + CUSTOMER + "/{key}"), paths.keySet().toString());
        assertFalse(paths.containsKey("/api/" + ORDER), "only the asked table");

        String props = "components.schemas." + CUSTOMER + ".properties.";
        assertEquals("string", doc.getString(props + "email.type"));
        assertEquals("email", doc.getString(props + "email.format"));
        assertEquals("date", doc.getString(props + "birth.format"));
        assertEquals("boolean", doc.getString(props + "active.type"));
        assertEquals("number", doc.getString(props + "score.type"));
        assertEquals(List.of("new", "approved", "closed"), doc.getList(props + "status.enum"));
        assertEquals(true, doc.getBoolean(props + "uuid.readOnly"));
        assertEquals(List.of("full_name"), doc.getList("components.schemas." + CUSTOMER + ".required"));

        List<String> params = doc.getList("paths.'/api/" + CUSTOMER + "'.get.parameters.name");
        // text, ordered (date / number), boolean filters + paging
        for (String p : List.of("_limit", "_start", "_sort", "select_fields", "full_name", "full_name_containss",
                "full_name_rlike", "full_name_in", "full_name_nn", "birth_gte", "birth_lt", "score_gt", "active",
                "active_inn", "status_in")) {
            assertTrue(params.contains(p), p + " missing in " + params);
        }
        assertFalse(params.contains("active_gt"), "no range filter on a boolean");
        assertFalse(params.contains("birth_containss"), "no text filter on a date");

        // public table: reads without security, writes with
        assertNull(doc.get("paths.'/api/" + CUSTOMER + "'.get.security"));
        assertNotNull(doc.get("paths.'/api/" + CUSTOMER + "'.post.security"));
        assertNotNull(doc.get("components.securitySchemes.bearerAuth"));
        assertFalse("null".equals(doc.getString("info.version")), "info.version must be set");
        // nothing pre-filled in Swagger UI "Try it out": no example on any list parameter
        List<Map<String, Object>> all = doc.getList("paths.'/api/" + CUSTOMER + "'.get.parameters");
        assertTrue(all.stream().noneMatch(p -> p.containsKey("example")), "parameters with example: " + all);
    }

    @Test
    void protectedTableAndJoin() {
        seed();
        String raw = given().when().get("/openapi/data/" + ORDER).then().statusCode(200).extract().asString();
        writeSample("data-openapi-" + ORDER + ".json", raw);
        JsonPath doc = new JsonPath(raw);
        assertNotNull(doc.get("paths.'/api/" + ORDER + "'.get.security"), "api protected: reads need a token");
        assertTrue(doc.getString("paths.'/api/" + ORDER + "'.get.description").contains("owner"));
        assertTrue(doc.getString("components.schemas." + ORDER + ".properties.customer_id.description").contains("/api/" + CUSTOMER));
        List<String> params = doc.getList("paths.'/api/" + ORDER + "'.get.parameters.name");
        assertTrue(params.contains("customer_id_in"), params.toString());
        assertFalse(params.contains("customer_id_containss"), "a join key is not text-searched");
    }

    @Test
    void groupAndTables() {
        seed();
        JsonPath doc = given().queryParam("group", "anagrafiche").when().get("/openapi/data").then().statusCode(200)
                .extract().jsonPath();
        Map<String, Object> paths = doc.getMap("paths");
        assertTrue(paths.containsKey("/api/" + CUSTOMER));
        assertFalse(paths.containsKey("/api/" + ORDER));
        given().queryParam("group", "no-such-group").when().get("/openapi/data").then().statusCode(404);
        given().when().get("/openapi/data/no_such_table").then().statusCode(404);
        List<String> tables = given().when().get("/openapi/data/tables").then().statusCode(200).extract().jsonPath().getList("");
        assertTrue(tables.containsAll(List.of(CUSTOMER, ORDER)), tables.toString());
    }

    /** Every documented filter of a list must be accepted by the real list endpoint. */
    @Test
    void everyDocumentedFilterWorks() {
        seed();
        JsonPath doc = given().when().get("/openapi/data/" + CUSTOMER).then().statusCode(200).extract().jsonPath();
        List<Map<String, Object>> params = doc.getList("paths.'/api/" + CUSTOMER + "'.get.parameters");
        List<String> failed = new ArrayList<>();
        for (Map<String, Object> p : params) {
            String name = (String) p.get("name");
            String value = sample(name, (Map<?, ?>) p.get("schema"), Boolean.TRUE.equals(p.get("allowEmptyValue")));
            int status = given().queryParam(name, value).when().get("/api/" + CUSTOMER).then().extract().statusCode();
            if (status != 200) {
                failed.add(name + "=" + value + " -> " + status);
            }
        }
        assertTrue(failed.isEmpty(), "filters documented but refused: " + failed);
        // and one that matches: case-insensitive contains finds the seeded row
        given().queryParam("full_name_containss", "ROSSI").when().get("/api/" + CUSTOMER).then().statusCode(200)
                .body("size()", equalTo(1));
    }

    /** Generated documents land in target/ for an external OpenAPI validator (dev/scripts or CI). */
    private static void writeSample(String name, String json) {
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("target", name), json);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static String sample(String name, Map<?, ?> schema, boolean valueless) {
        if (valueless) {
            return "";
        }
        switch (name) {
            case "_limit", "_start" -> { return "1"; }
            case "_sort" -> { return "full_name:desc"; }
            case "select_fields" -> { return "uuid,full_name"; }
            default -> { }
        }
        if (name.endsWith("_in")) {
            return name.startsWith("score") ? "1,5" : name.startsWith("birth") ? "2020-01-01,2021-01-01" : "a,b";
        }
        String type = String.valueOf(schema.get("type"));
        String format = String.valueOf(schema.get("format"));
        if ("date".equals(format)) {
            return "2020-01-01";
        }
        return switch (type) {
            case "integer" -> "1";
            case "number" -> "1.5";
            case "boolean" -> "true";
            default -> "x";
        };
    }

    private static String metadata(String table, boolean protectedApi, String usernameField, String description, String group) {
        Map<String, Object> m = new HashMap<>();
        m.put("table_name", table);
        m.put("table_key", "uuid");
        m.put("table_key_type", "uuid");
        m.put("description", description);
        m.put("metadata_group", group);
        m.put("api_protected", protectedApi);
        if (usernameField != null) {
            m.put("username_field", usernameField);
        }
        m.put("already_exist", false);
        m.put("created", false);
        return given().contentType(ContentType.JSON).body(m).when().post("/api/metadatas").then().statusCode(200)
                .extract().jsonPath().getString("uuid");
    }

    private static void field(String metadataUuid, String table, Map<String, Object> values) {
        Map<String, Object> f = new HashMap<>(values);
        f.put("metadata_uuid", metadataUuid);
        f.put("metadata_name", table);
        f.put("show_in_list", true);
        given().contentType(ContentType.JSON).body(f).when().post("/api/fielddefinitions").then().statusCode(200);
    }
}
