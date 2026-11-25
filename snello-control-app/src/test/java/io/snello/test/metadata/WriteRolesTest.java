package io.snello.test.metadata;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * metadatas.write_roles: a table readable by everyone (not api_protected) whose rows only some roles may create,
 * change or delete, e.g. a price list only the staff edits. Empty write_roles keeps the old rule (any user).
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WriteRolesTest {

    private static final String TABLE = "wr_rates";
    private static final String OPEN = "wr_notes";
    private static String rowId;

    @Test
    @Order(1)
    @TestSecurity(user = "staff", roles = {"Admin"})
    void adminCreatesTablesAndWrites() {
        cleanup();
        String meta = metadata(TABLE, "Admin,Manager");
        field(meta, TABLE, Map.of("name", "name", "label", "Name", "type", "input", "input_type", "text", "view_index", 1));
        given().when().post("/api/metadatas/{uuid}/create", meta).then().statusCode(200);
        String open = metadata(OPEN, null);
        field(open, OPEN, Map.of("name", "name", "label", "Name", "type", "input", "input_type", "text", "view_index", 1));
        given().when().post("/api/metadatas/{uuid}/create", open).then().statusCode(200);

        rowId = given().contentType(ContentType.JSON).body(Map.of("name", "daily")).when().post("/api/" + TABLE)
                .then().statusCode(200).extract().jsonPath().getString("uuid");
        given().contentType(ContentType.JSON).body(Map.of("name", "daily+")).when().put("/api/" + TABLE + "/" + rowId)
                .then().statusCode(200);
    }

    @Test
    @Order(2)
    @TestSecurity(user = "mario", roles = {"User"})
    void userCannotWriteButCanRead() {
        given().contentType(ContentType.JSON).body(Map.of("name", "cheap")).when().post("/api/" + TABLE)
                .then().statusCode(403).body("msg", containsString("only Admin,Manager can change " + TABLE));
        given().contentType(ContentType.JSON).body(Map.of("name", "free")).when().put("/api/" + TABLE + "/" + rowId)
                .then().statusCode(403);
        given().when().delete("/api/" + TABLE + "/" + rowId).then().statusCode(403);
        given().when().get("/api/" + TABLE + "/" + rowId).then().statusCode(200).body("name", equalTo("daily+"));
        // a table without write_roles: unchanged, any user writes
        given().contentType(ContentType.JSON).body(Map.of("name", "mine")).when().post("/api/" + OPEN)
                .then().statusCode(200);
    }

    @Test
    @Order(3)
    @TestSecurity(user = "boss", roles = {"Manager"})
    void managerWritesAndOpenApiSaysWho() {
        given().contentType(ContentType.JSON).body(Map.of("name", "weekly")).when().post("/api/" + TABLE)
                .then().statusCode(200);
        given().when().delete("/api/" + TABLE + "/" + rowId).then().statusCode(200);
        given().when().get("/openapi/data/" + TABLE).then().statusCode(200)
                .body(containsString("Login required, role: Admin, Manager."));
    }

    @Test
    @Order(4)
    @TestSecurity(user = "staff", roles = {"Admin"})
    void cleanupTables() {
        cleanup();
    }

    private void cleanup() {
        for (String t : List.of(TABLE, OPEN)) {
            List<Map> existing = given().queryParam("table_name", t).queryParam("_limit", 100)
                    .when().get("/api/metadatas").then().statusCode(200).extract().jsonPath().getList("", Map.class);
            for (Map m : existing) {
                given().when().post("/api/metadatas/{uuid}/delete", m.get("uuid"));
                given().when().delete("/api/metadatas/{id}", m.get("uuid")).then().statusCode(200);
            }
        }
    }

    private static String metadata(String table, String writeRoles) {
        Map<String, Object> m = new HashMap<>();
        m.put("table_name", table);
        m.put("table_key", "uuid");
        m.put("table_key_type", "uuid");
        m.put("description", "write roles test");
        m.put("api_protected", false);
        m.put("already_exist", false);
        m.put("created", false);
        if (writeRoles != null) {
            m.put("write_roles", writeRoles);
        }
        return given().contentType(ContentType.JSON).body(m).when().post("/api/metadatas").then().statusCode(200)
                .extract().jsonPath().getString("uuid");
    }

    private static void field(String metadataUuid, String table, Map<String, Object> values) {
        Map<String, Object> f = new HashMap<>(values);
        f.put("metadata_uuid", metadataUuid);
        f.put("metadata_name", table);
        given().contentType(ContentType.JSON).body(f).when().post("/api/fielddefinitions").then().statusCode(200);
    }
}
