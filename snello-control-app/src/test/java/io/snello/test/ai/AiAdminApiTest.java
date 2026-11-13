package io.snello.test.ai;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.snello.service.ApiService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static io.snello.management.AppConstants.*;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

/**
 * REST behaviour the snello-admin AI pages rely on: admin-wide chat history ({@code scope=all}) and optional text
 * columns left empty by the forms.
 */
@QuarkusTest
class AiAdminApiTest {

    static final String ALICE = "alice-history";

    @Inject
    ApiService apiService;

    /** Unique per test run so repeated runs against the same DB do not collide. */
    String run;
    /** Per run, because @BeforeEach seeds a fresh bob session before every test and the counts below are per user. */
    String bob;

    @BeforeEach
    void seedHistory() throws Exception {
        // fully qualified: the AppConstants.* static import brings a String UUID that shadows java.util.UUID
        run = java.util.UUID.randomUUID().toString().substring(0, 8);
        bob = "bob-history-" + run;
        session("s-alice-" + run, ALICE, 2);
        session("s-bob-" + run, bob, 3);
        session("s-bob-empty-" + run, bob, 0);
        event("s-bob-" + run, bob, "request", "hello", 0);
        event("s-bob-" + run, bob, "response", "hi bob", 1);
    }

    @Test
    @TestSecurity(user = ALICE, roles = {"User"})
    void userSeesOnlyOwnSessionsEvenAskingScopeAll() {
        Set<String> mine = sessionIds("/api/ai/history/sessions?scope=all&_limit=200");
        assertTrue(mine.contains("s-alice-" + run), mine.toString());
        assertFalse(mine.contains("s-bob-" + run), mine.toString());

        given().when().get("/api/ai/history/sessions/{id}", "hs-s-bob-" + run).then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin-history", roles = {"Admin"})
    void adminScopeAllListsEveryUserAndHidesEmptySessions() {
        Set<String> all = sessionIds("/api/ai/history/sessions?scope=all&_limit=200");
        assertTrue(all.containsAll(Set.of("s-alice-" + run, "s-bob-" + run)), all.toString());
        assertFalse(all.contains("s-bob-empty-" + run), "empty sessions are hidden: " + all);

        // without scope=all an admin still gets only their own (the chat widget behaviour)
        assertFalse(sessionIds("/api/ai/history/sessions?_limit=200").contains("s-bob-" + run));

        // the total count must be the filtered total, not the page size, or the admin paginator breaks
        String total = given().when().get("/api/ai/history/sessions?scope=all&user_id={u}&_limit=1", bob)
                .then().statusCode(200).extract().header("x-total-count");
        assertEquals("1", total, "bob has one non-empty session");

        List<Map<String, Object>> transcript = given().accept(ContentType.JSON)
                .when().get("/api/ai/history?scope=all&session_id={s}&_sort=occurred_at asc", "s-bob-" + run)
                .then().statusCode(200).extract().jsonPath().getList("$");
        assertEquals(List.of("hello", "hi bob"), transcript.stream().map(e -> e.get("body")).toList());

        given().when().get("/api/ai/history/sessions/{id}", "hs-s-bob-" + run).then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "admin-forms", roles = {"Admin"})
    void optionalTextColumnsMayBeLeftEmpty() {
        String projectUuid = given().contentType(ContentType.JSON)
                .body(Map.of("name", "forms-" + run))
                .when().post("/api/ai/projects")
                .then().statusCode(200)
                .body("roles", equalTo(""))
                .body("global_message", equalTo(""))
                .extract().path("uuid");

        Map<String, Object> skill = new HashMap<>();
        skill.put("project_uuid", projectUuid);
        skill.put("name", "skill-" + run);
        skill.put("description", null);
        given().contentType(ContentType.JSON).body(skill)
                .when().post("/api/ai/skills")
                .then().statusCode(200)
                .body("description", equalTo(""))
                .body("instructions", equalTo(""));

        Map<String, Object> tool = new HashMap<>();
        tool.put("project_uuid", projectUuid);
        tool.put("name", "tool_" + run);
        tool.put("url", "/api/events");
        tool.put("parameters_json", "{\"type\":\"object\",\"properties\":{}}");
        String toolUuid = given().contentType(ContentType.JSON).body(tool)
                .when().post("/api/ai/mcp-tools")
                .then().statusCode(200)
                .body("description", equalTo(""))
                .extract().path("uuid");

        // a partial update that clears the description stores "" instead of violating NOT NULL
        Map<String, Object> clear = new HashMap<>();
        clear.put("description", null);
        given().contentType(ContentType.JSON).body(clear)
                .when().put("/api/ai/mcp-tools/{id}", toolUuid)
                .then().statusCode(200);
    }

    private Set<String> sessionIds(String url) {
        List<Map<String, Object>> rows = given().accept(ContentType.JSON)
                .when().get(url)
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
        return rows.stream().map(r -> (String) r.get("session_id")).collect(Collectors.toSet());
    }

    private void session(String sessionId, String userId, long messages) throws Exception {
        Map<String, Object> row = new HashMap<>();
        row.put("uuid", "hs-" + sessionId);
        row.put("session_id", sessionId);
        row.put("user_id", userId);
        row.put("message_count", messages);
        row.put("started_at", Timestamp.from(Instant.now()));
        apiService.createIfNotExists(HISTORY_SESSION, row, UUID_FIELD);
    }

    private void event(String sessionId, String userId, String type, String body, int secondsAfter) throws Exception {
        Map<String, Object> row = new HashMap<>();
        row.put("uuid", "he-" + sessionId + "-" + secondsAfter);
        row.put("occurred_at", Timestamp.from(Instant.now().plusSeconds(secondsAfter)));
        row.put("channel", "agui");
        row.put("user_id", userId);
        row.put("username", userId);
        row.put("type", type);
        row.put("body", body);
        row.put("session_id", sessionId);
        apiService.createIfNotExists(HISTORY_EVENT, row, UUID_FIELD);
    }

    private static final String UUID_FIELD = UUID;
}
