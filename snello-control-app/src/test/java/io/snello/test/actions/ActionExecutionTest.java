package io.snello.test.actions;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.snello.api.service.JdbcRepository;
import io.snello.service.MetadataService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressions from the end-to-end review of Actions:
 * <ul>
 *     <li>the Nashorn engine was looked up through ScriptEngineManager, which finds nothing under Quarkus: no action
 *         ever ran, and a PRE action made every write to its table fail with 500;</li>
 *     <li>PRE actions received {@code map = null} (the payload was read from the wrong argument);</li>
 *     <li>scripts had no way to know the row key ({@code table_key} is the key column name): now bound as
 *         {@code key}.</li>
 * </ul>
 */
@QuarkusTest
@TestSecurity(user = "act-admin", roles = {"Admin"})
class ActionExecutionTest {

    private static final String TABLE = "act_probe";
    private static final String AUDIT = "act_probe_audit";
    private static final String META = "act-probe-meta";

    @Inject
    JdbcRepository jdbc;

    @Inject
    MetadataService metadataService;

    @BeforeEach
    void seed() throws Exception {
        cleanup();
        jdbc.executeQuery("CREATE TABLE " + TABLE + " (uuid varchar(50) PRIMARY KEY, name varchar(50), notes varchar(200))");
        jdbc.executeQuery("CREATE TABLE " + AUDIT + " (event varchar(200))");
        Map<String, Object> m = new HashMap<>();
        m.put("uuid", META);
        m.put("table_name", TABLE);
        m.put("table_key", "uuid");
        m.put("table_key_type", "uuid");
        m.put("created", true);
        jdbc.create("metadatas", "uuid", m);
        metadataService.invalidateMetadataCaches();
    }

    @AfterEach
    void cleanup() throws Exception {
        jdbc.executeQuery("DROP TABLE IF EXISTS " + TABLE);
        jdbc.executeQuery("DROP TABLE IF EXISTS " + AUDIT);
        jdbc.executeQuery("DELETE FROM metadatas WHERE uuid = '" + META + "'");
        jdbc.executeQuery("DELETE FROM actions WHERE metadata_name = '" + TABLE + "'");
        metadataService.invalidateMetadataCaches();
        metadataService.invalidateSecondaryCaches();
    }

    private void action(String condition, String phase, String body) throws Exception {
        Map<String, Object> a = new HashMap<>();
        a.put("uuid", java.util.UUID.randomUUID().toString());
        a.put("name", "act-" + condition + "-" + phase);
        a.put("description", "test");
        a.put("metadata_name", TABLE);
        a.put("condition", condition);
        a.put("phase", phase);
        a.put("body", body);
        jdbc.create("actions", "uuid", a);
        metadataService.invalidateSecondaryCaches();
    }

    private String create(String name) {
        return given().contentType(ContentType.JSON).body(Map.of("name", name)).when().post("/api/" + TABLE)
                .then().statusCode(200).extract().path("uuid");
    }

    private List<String> audit() throws Exception {
        return jdbc.list("SELECT event FROM " + AUDIT).stream().map(r -> (String) r.get("event")).sorted().toList();
    }

    private List<String> awaitAudit(int expected) throws Exception {
        for (int i = 0; i < 50 && audit().size() < expected; i++) {
            Thread.sleep(100); // POST actions run asynchronously
        }
        return audit();
    }

    @Test
    void postActionsRunWithPayloadAndRowKey() throws Exception {
        String log = "db.query(\"INSERT INTO " + AUDIT + " (event) VALUES (?)\", java.util.List.of(";
        action("PERSIST", "POST", log + "'persist ' + key + ' ' + map.get('name') + ' ' + table_key));");
        action("MERGE", "POST", log + "'merge ' + key + ' ' + map.get('name')));");
        action("DELETE", "POST", log + "'delete ' + key));");

        String id = create("first");
        given().contentType(ContentType.JSON).body(Map.of("name", "renamed")).when().put("/api/" + TABLE + "/{id}", id)
                .then().statusCode(200);
        given().when().delete("/api/" + TABLE + "/{id}", id).then().statusCode(200);

        assertEquals(List.of("delete " + id, "merge " + id + " renamed", "persist " + id + " first uuid"), awaitAudit(3));
    }

    @Test
    void preActionCanChangeThePayload() throws Exception {
        action("PERSIST", "PRE", "map.put('notes', 'set by PRE for ' + map.get('name'));");
        String id = create("boat");
        assertEquals("set by PRE for boat", jdbc.list("SELECT notes FROM " + TABLE + " WHERE uuid = '" + id + "'").get(0).get("notes"));
    }

    @Test
    void failingPreActionBlocksTheWrite() throws Exception {
        action("PERSIST", "PRE", "if (map.get('name') == 'forbidden') throw 'name not allowed';");
        given().contentType(ContentType.JSON).body(Map.of("name", "forbidden")).when().post("/api/" + TABLE)
                .then().statusCode(400);
        assertTrue(jdbc.list("SELECT uuid FROM " + TABLE).isEmpty(), "blocked row must not be saved");
        create("allowed");
        assertEquals(1, jdbc.list("SELECT uuid FROM " + TABLE).size());
    }
}
