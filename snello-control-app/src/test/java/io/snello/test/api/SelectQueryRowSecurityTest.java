package io.snello.test.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.snello.api.service.JdbcRepository;
import io.snello.service.MetadataService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Row-level security on named select queries ({@code selectqueries.api_protected} + {@code username_field}),
 * same contract as protected metadata tables: anonymous → 401, non-admin → only own rows, admin → everything.
 * The owner value is always bound ({@code ?}), never concatenated.
 * <p>
 * Self-contained: creates its own table and select queries, so it does not depend on the dump seed.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SelectQueryRowSecurityTest {

    private static final String TABLE = "sq_sec_items";
    private static final String ALICE = "sq-alice";
    private static final String BOB = "sq-bob";

    /** Protected, SQL with its own top-level WHERE (hidden rows must stay hidden), not with_params. */
    private static final String Q_PLAIN = "sq_sec_plain";
    /** Protected, with_params, owner column is an alias. */
    private static final String Q_PARAMS = "sq_sec_params";
    /** Not protected: must keep working exactly as before. */
    private static final String Q_OPEN = "sq_sec_open";

    @Inject
    JdbcRepository jdbc;

    @Inject
    MetadataService metadataService;

    private boolean seeded;

    private void seed() throws Exception {
        if (seeded) {
            return;
        }
        cleanup();
        jdbc.executeQuery("CREATE TABLE " + TABLE + " (uuid varchar(50) PRIMARY KEY, username varchar(200), label varchar(50), hidden int)");
        row(ALICE, "alice-1", 0);
        row(ALICE, "alice-2", 0);
        row(ALICE, "alice-hidden", 1);
        row(BOB, "bob-1", 0);
        row(BOB, "bob-2", 0);
        selectQuery(Q_PLAIN, "SELECT uuid, username, label FROM " + TABLE + " WHERE hidden = 0;", false, true, "username");
        selectQuery(Q_PARAMS, "SELECT uuid, username AS owner, label FROM " + TABLE, true, true, "owner");
        selectQuery(Q_OPEN, "SELECT uuid, username, label FROM " + TABLE, false, false, null);
        metadataService.invalidateSecondaryCaches();
        seeded = true;
    }

    private void row(String owner, String label, int hidden) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("uuid", UUID.randomUUID().toString());
        m.put("username", owner);
        m.put("label", label);
        m.put("hidden", hidden);
        jdbc.create(TABLE, "uuid", m);
    }

    private void selectQuery(String name, String sql, boolean withParams, boolean apiProtected, String usernameField) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("uuid", UUID.randomUUID().toString());
        m.put("query_name", name);
        m.put("select_query", sql);
        // without a count query ApiService.count falls back to counting a *table* named like the query
        m.put("select_query_count", "SELECT COUNT(*) AS size_of FROM " + TABLE);
        m.put("with_params", withParams);
        m.put("api_protected", apiProtected);
        m.put("username_field", usernameField);
        jdbc.create("selectqueries", "uuid", m);
    }

    @AfterAll
    void cleanupAll() throws Exception {
        cleanup();
        metadataService.invalidateSecondaryCaches();
    }

    private void cleanup() throws Exception {
        jdbc.executeQuery("DROP TABLE IF EXISTS " + TABLE);
        jdbc.executeQuery("DELETE FROM selectqueries WHERE query_name IN ('" + Q_PLAIN + "','" + Q_PARAMS + "','" + Q_OPEN + "')");
    }

    private List<Map<String, Object>> list(String query, Map<String, Object> params, int expectedStatus) throws Exception {
        seed();
        var req = given().accept(ContentType.JSON).queryParam("_limit", 100);
        for (var e : params.entrySet()) {
            req = req.queryParam(e.getKey(), e.getValue());
        }
        var resp = req.when().get("/api/{q}", query).then().statusCode(expectedStatus);
        return expectedStatus == 200 ? resp.extract().jsonPath().getList("") : List.of();
    }

    private static List<String> labels(List<Map<String, Object>> rows) {
        return rows.stream().map(r -> (String) r.get("label")).sorted().toList();
    }

    // ------------------------------------------------------------------ anonymous

    @Test
    void anonymousIsRejectedOnProtectedSelectQuery() throws Exception {
        list(Q_PLAIN, Map.of(), 401);
        list(Q_PARAMS, Map.of(), 401);
    }

    @Test
    void anonymousStillReadsUnprotectedSelectQuery() throws Exception {
        assertEquals(5, list(Q_OPEN, Map.of(), 200).size());
    }

    // ------------------------------------------------------------------ non-admin

    @Test
    @TestSecurity(user = ALICE, roles = {"User"})
    void userSeesOnlyOwnRowsAndQueryWhereIsKept() throws Exception {
        // alice-hidden is excluded by the query's own WHERE, bob-* by the owner filter
        assertEquals(List.of("alice-1", "alice-2"), labels(list(Q_PLAIN, Map.of(), 200)));
    }

    @Test
    @TestSecurity(user = BOB, roles = {"User"})
    void ownerFilterWorksOnAliasedColumnWithParams() throws Exception {
        assertEquals(List.of("bob-1", "bob-2"), labels(list(Q_PARAMS, Map.of(), 200)));
        // other request filters still combine with the owner filter
        assertEquals(List.of("bob-2"), labels(list(Q_PARAMS, Map.of("label", "bob-2"), 200)));
        assertEquals(List.of(), labels(list(Q_PARAMS, Map.of("label", "alice-1"), 200)));
    }

    @Test
    @TestSecurity(user = ALICE, roles = {"User"})
    void callerCannotOverrideOwnerWithRequestParameter() throws Exception {
        assertEquals(List.of("alice-1", "alice-2"), labels(list(Q_PLAIN, Map.of("username", BOB), 200)));
        assertEquals(List.of("alice-1", "alice-2", "alice-hidden"), labels(list(Q_PARAMS, Map.of("owner", BOB), 200)));
    }

    @Test
    @TestSecurity(user = ALICE, roles = {"User"})
    void parameterNameCannotRewriteTheWhereClause() throws Exception {
        // would become "... AND 1=1 OR owner = ?" and step around the owner filter if the name were spliced raw
        list(Q_PARAMS, Map.of("1=1 OR owner", "x"), 400);
        list(Q_PARAMS, Map.of("label; DROP TABLE " + TABLE + " --", "x"), 400);
        assertEquals(5, jdbc.list(TABLE, null).size(), "table must be untouched");
    }

    @Test
    @TestSecurity(user = ALICE, roles = {"User"})
    void unprotectedSelectQueryIsUnchangedForUsers() throws Exception {
        assertEquals(5, list(Q_OPEN, Map.of(), 200).size());
    }

    // ------------------------------------------------------------------ admin

    @Test
    @TestSecurity(user = "sq-admin", roles = {"Admin"})
    void adminSeesAllRowsOfQuery() throws Exception {
        assertEquals(List.of("alice-1", "alice-2", "bob-1", "bob-2"), labels(list(Q_PLAIN, Map.of(), 200)));
        assertEquals(5, list(Q_PARAMS, Map.of(), 200).size());
    }

    // ------------------------------------------------------------------ definition validation

    @Test
    @TestSecurity(user = "sq-admin", roles = {"Admin"})
    void protectedSelectQueryRequiresSafeUsernameField() throws Exception {
        seed();
        Map<String, Object> body = new HashMap<>();
        body.put("query_name", "sq_sec_invalid");
        body.put("select_query", "SELECT 1");
        body.put("api_protected", true);
        given().contentType(ContentType.JSON).body(body).when().post("/api/selectqueries").then().statusCode(400);
        // not an identifier
        body.put("username_field", "owner; drop table x");
        given().contentType(ContentType.JSON).body(body).when().post("/api/selectqueries").then().statusCode(400);
        // operator suffix: ParamUtils would read "owner_ne = caller" as "owner != caller"
        body.put("username_field", "owner_ne");
        given().contentType(ContentType.JSON).body(body).when().post("/api/selectqueries").then().statusCode(400);
    }

    // ------------------------------------------------------------------ count

    @Test
    @TestSecurity(user = ALICE, roles = {"User"})
    void totalCountIsOwnerScoped() throws Exception {
        seed();
        // select_query_count counts the whole table (5): the protected count must use the owner-scoped wrap instead
        assertEquals("2", given().accept(ContentType.JSON).when().get("/api/{q}", Q_PLAIN).then().statusCode(200)
                .extract().header("x-total-count"));
        assertEquals("3", given().accept(ContentType.JSON).when().get("/api/{q}", Q_PARAMS).then().statusCode(200)
                .extract().header("x-total-count"));
        assertEquals("1", given().accept(ContentType.JSON).queryParam("label", "alice-2")
                .when().get("/api/{q}", Q_PARAMS).then().statusCode(200).extract().header("x-total-count"));
    }

    // ------------------------------------------------------------------ SQL text parameters

    @Test
    @TestSecurity(user = ALICE, roles = {"User"})
    void sortSubqueryIsRejectedOnSelectQuery() throws Exception {
        list(Q_PARAMS, Map.of("_sort", "(SELECT 1/(CASE WHEN (SELECT count(*) FROM " + TABLE
                + " WHERE username='" + BOB + "')=2 THEN 0 ELSE 1 END))"), 400);
        assertEquals(List.of("alice-hidden", "alice-2", "alice-1"), list(Q_PARAMS, Map.of("_sort", "label:desc"), 200)
                .stream().map(r -> (String) r.get("label")).toList());
    }

    @Test
    @TestSecurity(user = "sq-admin", roles = {"Admin"})
    void securityColumnsRoundTripThroughRestApi() throws Exception {
        seed();
        List<Map<String, Object>> rows = given().accept(ContentType.JSON).queryParam("query_name", Q_PARAMS)
                .when().get("/api/selectqueries").then().statusCode(200).extract().jsonPath().getList("");
        assertEquals(1, rows.size());
        assertEquals(Boolean.TRUE, rows.get(0).get("api_protected"));
        assertEquals("owner", rows.get(0).get("username_field"));
        assertTrue(metadataService.selectqueryMap().get(Q_PARAMS).api_protected);
    }
}
