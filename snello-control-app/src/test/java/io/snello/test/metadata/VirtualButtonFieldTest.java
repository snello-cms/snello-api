package io.snello.test.metadata;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;

/**
 * A {@code button} field is virtual: a form action (e.g. "scan document" pre-filling other fields), no column.
 * CREATE TABLE and a button added later skip it; a value sent for it on POST / PUT is dropped instead of failing.
 */
@QuarkusTest
@TestSecurity(user = "test-admin", roles = {"Admin"})
class VirtualButtonFieldTest {

    private static final String TABLE = "vb_customer";

    @Test
    void buttonFieldHasNoColumnAndItsValueIsIgnored() {
        cleanup();
        String meta = metadata();
        field(meta, Map.of("name", "full_name", "label", "Full name", "type", "input", "input_type", "text", "view_index", 1));
        field(meta, Map.of("name", "scan_document", "label", "Scan document", "type", "button",
                "function_def", "{\"action\":\"prefill\",\"url\":\"http://localhost:28090/api/scan\"}", "view_index", 2));
        given().when().post("/api/metadatas/{uuid}/create", meta).then().statusCode(200);

        // the admin form sends every control, the button's too
        Map<String, Object> row = new HashMap<>();
        row.put("full_name", "Rossi Mario");
        row.put("scan_document", null);
        String uuid = given().contentType(ContentType.JSON).body(row).when().post("/api/" + TABLE)
                .then().statusCode(200).extract().jsonPath().getString("uuid");

        row.put("uuid", uuid);
        row.put("full_name", "Rossi Maria");
        row.put("scan_document", "clicked");
        given().contentType(ContentType.JSON).body(row).when().put("/api/" + TABLE + "/" + uuid).then().statusCode(200);

        given().when().get("/api/" + TABLE + "/" + uuid).then().statusCode(200)
                .body("full_name", equalTo("Rossi Maria"))
                .body("$", not(hasKey("scan_document")));

        // a button added to the existing table: still no column, writes keep working
        field(meta, Map.of("name", "second_button", "label", "Another", "type", "button", "view_index", 3));
        Map<String, Object> again = new HashMap<>();
        again.put("full_name", "Bianchi Luca");
        again.put("second_button", "x");
        given().contentType(ContentType.JSON).body(again).when().post("/api/" + TABLE).then().statusCode(200);
        cleanup();
    }

    private void cleanup() {
        List<Map> existing = given().queryParam("table_name", TABLE).queryParam("_limit", 100)
                .when().get("/api/metadatas").then().statusCode(200).extract().jsonPath().getList("", Map.class);
        for (Map m : existing) {
            given().when().post("/api/metadatas/{uuid}/delete", m.get("uuid"));
            given().when().delete("/api/metadatas/{id}", m.get("uuid")).then().statusCode(200);
        }
    }

    private static String metadata() {
        Map<String, Object> m = new HashMap<>();
        m.put("table_name", TABLE);
        m.put("table_key", "uuid");
        m.put("table_key_type", "uuid");
        m.put("description", "virtual button test");
        m.put("api_protected", false);
        m.put("already_exist", false);
        m.put("created", false);
        return given().contentType(ContentType.JSON).body(m).when().post("/api/metadatas").then().statusCode(200)
                .extract().jsonPath().getString("uuid");
    }

    private static void field(String metadataUuid, Map<String, Object> values) {
        Map<String, Object> f = new HashMap<>(values);
        f.put("metadata_uuid", metadataUuid);
        f.put("metadata_name", TABLE);
        given().contentType(ContentType.JSON).body(f).when().post("/api/fielddefinitions").then().statusCode(200);
    }
}
