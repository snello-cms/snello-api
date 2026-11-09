package io.snello.test.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.snello.api.service.JdbcRepository;
import io.snello.service.MetadataService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regressions from the end-to-end review:
 * <ul>
 *     <li>table DDL ({@code /api/metadatas/{id}/create|delete|truncate}) was a GET, and every GET on /api/* is
 *         public: an anonymous request truncated or dropped tables;</li>
 *     <li>any authenticated {@code User} could write configuration (select queries / conditions with raw SQL,
 *         server-side JavaScript actions, metadata).</li>
 * </ul>
 * Runs on a throwaway table so a regression cannot destroy seed data.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfigWriteAuthorizationTest {

    private static final String TABLE = "cfg_auth_probe";
    private static final String META = "cfg-auth-probe-meta";

    @Inject
    JdbcRepository jdbc;

    @Inject
    MetadataService metadataService;

    @BeforeAll
    void seed() throws Exception {
        cleanup();
        jdbc.executeQuery("CREATE TABLE " + TABLE + " (uuid varchar(50) PRIMARY KEY, name varchar(50))");
        jdbc.executeQuery("INSERT INTO " + TABLE + " (uuid, name) VALUES ('r1', 'kept')");
        Map<String, Object> m = new HashMap<>();
        m.put("uuid", META);
        m.put("table_name", TABLE);
        m.put("table_key", "uuid");
        m.put("table_key_type", "uuid");
        m.put("created", true);
        jdbc.create("metadatas", "uuid", m);
        metadataService.invalidateMetadataCaches();
    }

    @AfterAll
    void cleanup() throws Exception {
        jdbc.executeQuery("DROP TABLE IF EXISTS " + TABLE);
        jdbc.executeQuery("DELETE FROM metadatas WHERE uuid = '" + META + "'");
        jdbc.executeQuery("DELETE FROM selectqueries WHERE query_name LIKE 'cfg_auth_%'");
        metadataService.invalidateMetadataCaches();
        metadataService.invalidateSecondaryCaches();
    }

    private long rows() throws Exception {
        return jdbc.list("SELECT uuid FROM " + TABLE).size();
    }

    // ------------------------------------------------------------------ table DDL

    @Test
    void anonymousCannotTruncateOrDropTables() throws Exception {
        for (String op : List.of("truncate", "delete", "create")) {
            given().when().get("/api/metadatas/{id}/" + op, META).then().statusCode(405);
            given().when().post("/api/metadatas/{id}/" + op, META).then().statusCode(401);
        }
        assertEquals(1, rows(), "table must be untouched");
    }

    @Test
    @TestSecurity(user = "cfg-user", roles = {"User"})
    void userCannotTruncate() throws Exception {
        given().when().post("/api/metadatas/{id}/truncate", META).then().statusCode(403);
        assertEquals(1, rows());
    }

    @Test
    @TestSecurity(user = "cfg-manager", roles = {"Manager"})
    void managerCannotRunTableDdl() throws Exception {
        given().when().post("/api/metadatas/{id}/truncate", META).then().statusCode(403);
        assertEquals(1, rows());
    }

    // ------------------------------------------------------------------ configuration writes

    private static Map<String, Object> selectQuery(String name) {
        Map<String, Object> q = new HashMap<>();
        q.put("query_name", name);
        q.put("select_query", "SELECT uuid FROM " + TABLE);
        q.put("select_query_count", "SELECT COUNT(*) AS size_of FROM " + TABLE);
        return q;
    }

    @Test
    void anonymousCannotWriteConfiguration() {
        given().contentType(ContentType.JSON).body(selectQuery("cfg_auth_anon")).when().post("/api/selectqueries")
                .then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "cfg-user", roles = {"User"})
    void userCannotWriteAnyConfiguration() {
        Map<String, Object> any = Map.of("name", "x", "description", "x", "metadata_name", TABLE,
                "table_name", "cfg_auth_user_meta", "table_key", "uuid", "condition", "PERSIST", "body", "1");
        for (String resource : List.of("selectqueries", "conditions", "actions", "metadatas", "fielddefinitions", "links")) {
            given().contentType(ContentType.JSON).body(any).when().post("/api/" + resource).then().statusCode(403);
            given().contentType(ContentType.JSON).body(any).when().put("/api/" + resource + "/{id}", META).then().statusCode(403);
            given().when().delete("/api/" + resource + "/{id}", META).then().statusCode(403);
        }
        // reading configuration stays open
        given().when().get("/api/selectqueries").then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "cfg-manager", roles = {"Manager"})
    void managerCanWriteConfiguration() {
        given().contentType(ContentType.JSON).body(selectQuery("cfg_auth_manager")).when().post("/api/selectqueries")
                .then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "cfg-admin", roles = {"Admin"})
    void conditionFromTheAdminEditorCanBeSaved() throws Exception {
        // the editor sends neither metadata_multijoin_uuid nor (optionally) query_params; both are NOT NULL
        Map<String, Object> c = new HashMap<>();
        c.put("metadata_uuid", META);
        c.put("metadata_name", TABLE);
        c.put("condition", "q_nn");
        c.put("sub_query", "name = ?");
        c.put("separator", "AND");
        String id = given().contentType(ContentType.JSON).body(c).when().post("/api/conditions")
                .then().statusCode(200).extract().path("uuid");
        jdbc.executeQuery("DELETE FROM conditions WHERE uuid = '" + id + "'");
        metadataService.invalidateSecondaryCaches();
    }

    @Test
    @TestSecurity(user = "cfg-admin", roles = {"Admin"})
    void adminCanTruncate() throws Exception {
        jdbc.executeQuery("INSERT INTO " + TABLE + " (uuid, name) VALUES ('r-tmp', 'tmp')");
        given().when().post("/api/metadatas/{id}/truncate", META).then().statusCode(200);
        assertEquals(0, rows());
        jdbc.executeQuery("INSERT INTO " + TABLE + " (uuid, name) VALUES ('r1', 'kept')");
    }
}
