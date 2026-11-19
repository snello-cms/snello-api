package io.snello.test.flow;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.snello.api.service.JdbcRepository;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A human task follows its workflow instance: suspended = decisions on hold (409, page told why), ended (cancelled /
 * faulted) = task CANCELLED, cancel from the monitor = its OPEN tasks CANCELLED at once, bridge unknown = task rules only.
 * A fake flow-bridge (GET /instances/{id}) and runner (POST /runner/q/flow/exec/...) answer on 18391.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HumanTaskWorkflowStateTest {

    private static final String TASKS = "/api/humantasks";

    @Inject
    JdbcRepository jdbc;

    HttpServer fake;
    /** instance id -> status the fake bridge reports; missing = 404 (bridge does not know it) */
    final Map<String, String> instanceStatus = new ConcurrentHashMap<>();
    final List<String> runnerCalls = new CopyOnWriteArrayList<>();

    @BeforeAll
    void start() throws Exception {
        fake = HttpServer.create(new InetSocketAddress("127.0.0.1", 18391), 0);
        fake.createContext("/instances/", ex -> {
            String id = ex.getRequestURI().getPath().substring("/instances/".length());
            String st = instanceStatus.get(id);
            String body = st == null ? "{\"error\": \"unknown instance\"}"
                    : "{\"instanceId\": \"" + id + "\", \"status\": \"" + st + "\", \"currentTask\": null, \"tasks\": {}}";
            reply(ex, st == null ? 404 : 200, body);
        });
        fake.createContext("/runner/", ex -> {
            runnerCalls.add(ex.getRequestMethod() + " " + ex.getRequestURI().getPath());
            reply(ex, 200, "{}");
        });
        fake.start();
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws java.io.IOException {
        byte[] out = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, out.length);
        ex.getResponseBody().write(out);
        ex.close();
    }

    @AfterAll
    void stop() throws Exception {
        fake.stop(0);
        jdbc.executeQuery("DELETE FROM humantasks WHERE workflow_namespace = 'htstate'");
    }

    private String task(String instanceId) {
        return given().contentType(ContentType.JSON).body(Map.of(
                        "title", "Review " + instanceId, "workflow_namespace", "htstate", "workflow_name", "review",
                        "workflow_version", "1.0.0", "instance_id", instanceId, "decisions", "APPROVE,REFUSE",
                        "roles", "Admin,Manager"))
                .post(TASKS).then().statusCode(200).body("status", equalTo("OPEN"))
                .extract().path("uuid");
    }

    private io.restassured.response.ValidatableResponse decide(String id, String decision) {
        return given().contentType(ContentType.JSON).body(Map.of("decision", decision))
                .post(TASKS + "/" + id + "/decide").then();
    }

    @Test
    @TestSecurity(user = "ht-admin", roles = {"Admin"})
    void suspendedWorkflowPutsDecisionsOnHold() {
        instanceStatus.put("HT-SUSP", "SUSPENDED");
        String id = task("HT-SUSP");

        given().get(TASKS + "/" + id + "/context").then().statusCode(200)
                .body("task.status", equalTo("OPEN"))
                .body("hold", containsString("suspended"));
        decide(id, "APPROVE").statusCode(409).body("msg", containsString("suspended"));

        instanceStatus.put("HT-SUSP", "WAITING");   // resumed
        given().get(TASKS + "/" + id + "/context").then().statusCode(200).body("hold", nullValue());
        decide(id, "APPROVE").statusCode(200).body("decision", equalTo("APPROVE"));

        instanceStatus.put("HT-SUSP", "SUSPENDED");  // UNDO is a decision too
        given().contentType(ContentType.JSON).post(TASKS + "/" + id + "/undo").then().statusCode(409)
                .body("msg", containsString("suspended"));
    }

    @Test
    @TestSecurity(user = "ht-admin", roles = {"Admin"})
    void endedWorkflowCancelsTheOpenTask() {
        instanceStatus.put("HT-CANC", "CANCELLED");
        String id = task("HT-CANC");

        given().get(TASKS + "/" + id + "/context").then().statusCode(200)
                .body("task.status", equalTo("CANCELLED"))
                .body("task.decision_comment", containsString("workflow cancelled"))
                .body("hold", nullValue());
        decide(id, "APPROVE").statusCode(409).body("msg", containsString("CANCELLED"));

        // reached through decide first (no page load in between): same outcome, clear message
        instanceStatus.put("HT-FAULT", "FAULTED");
        String id2 = task("HT-FAULT");
        decide(id2, "REFUSE").statusCode(409).body("msg", containsString("faulted"));
        given().get(TASKS + "/" + id2).then().body("status", equalTo("CANCELLED"));
    }

    @Test
    @TestSecurity(user = "ht-admin", roles = {"Admin"})
    void unknownToTheBridgeDecidesAsBefore() {
        String id = task("HT-UNKNOWN");   // fake bridge answers 404: a bridge outage must not block decisions
        decide(id, "REFUSE").statusCode(200).body("decision", equalTo("REFUSE"));
    }

    @Test
    @TestSecurity(user = "ht-admin", roles = {"Admin"})
    void cancelFromTheMonitorCancelsItsOpenTasks() {
        instanceStatus.put("HT-MON", "WAITING");
        String id = task("HT-MON");
        String other = task("HT-OTHER");

        given().contentType(ContentType.JSON).post("/api/flow/instances/htstate/review/1.0.0/HT-MON/cancel")
                .then().statusCode(200);
        assertTrue(runnerCalls.contains("POST /runner/q/flow/exec/htstate/review/1.0.0/HT-MON/cancel"), runnerCalls.toString());

        given().get(TASKS + "/" + id).then().body("status", equalTo("CANCELLED"))
                .body("decision_comment", containsString("workflow cancelled by ht-admin"));
        given().get(TASKS + "/" + other).then().body("status", equalTo("OPEN"));
    }
}
