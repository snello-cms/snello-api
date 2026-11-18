package io.snello.test.flow;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.snello.api.service.JdbcRepository;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Workflow definitions: YAML checks (DSL name rules, anchors, document block), uniqueness, DRAFT -> DEPLOYED
 * immutability, deploy through flow-bridge (fake bridge on 127.0.0.1:18391, see test application.properties) with its
 * success / refusal answers, delete of a deployed workflow removing its file, and write access (Admin / Manager).
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkflowDefinitionsTest {

    private static final String PATH = "/api/workflowdefinitions";

    @Inject
    JdbcRepository jdbc;

    HttpServer bridge;
    final List<String> calls = new CopyOnWriteArrayList<>();
    volatile int putStatus = 201;
    volatile String putBody = "{\"seconds\": 1.5}";

    @BeforeAll
    void startBridge() throws Exception {
        bridge = HttpServer.create(new InetSocketAddress("127.0.0.1", 18391), 0);
        bridge.createContext("/workflows", ex -> {
            String token = ex.getRequestHeaders().getFirst("X-Bridge-Token");
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            calls.add(ex.getRequestMethod() + " " + ex.getRequestURI().getPath() + " token=" + token
                    + " yaml=" + body.contains("document:"));
            int status = "PUT".equals(ex.getRequestMethod()) ? putStatus : 200;
            String answer = "PUT".equals(ex.getRequestMethod()) ? putBody
                    : "GET".equals(ex.getRequestMethod()) ? "{\"writes\": true, \"workflows\": []}" : "{\"removed\": true}";
            byte[] out = answer.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        bridge.start();
    }

    @AfterAll
    void stopBridge() throws Exception {
        bridge.stop(0);
        jdbc.executeQuery("DELETE FROM workflowdefinitions WHERE namespace = 'wftest'");
    }

    @BeforeEach
    void reset() throws Exception {
        jdbc.executeQuery("DELETE FROM workflowdefinitions WHERE namespace = 'wftest'");
        calls.clear();
        putStatus = 201;
        putBody = "{\"seconds\": 1.5}";
    }

    static String yaml(String name, String version) {
        return """
                document:
                  dsl: "1.0.0"
                  namespace: wftest
                  name: %s
                  version: "%s"
                  title: Hello %s
                do:
                  - greet:
                      set:
                        message: '${ "Hello, " + .name + "!" }'
                """.formatted(name, version, name);
    }

    private String create(String yaml) {
        return given().contentType(ContentType.JSON).body(Map.of("yaml", yaml))
                .post(PATH).then().statusCode(200)
                .body("status", equalTo("DRAFT"))
                .extract().path("uuid");
    }

    @Test
    @TestSecurity(user = "wf-admin", roles = {"Admin"})
    void createDerivesColumnsFromTheYaml() {
        String id = create(yaml("hello", "1.0.0"));
        given().get(PATH + "/" + id).then().statusCode(200)
                .body("namespace", equalTo("wftest"))
                .body("name", equalTo("hello"))
                .body("version", equalTo("1.0.0"))
                .body("title", equalTo("Hello hello"))
                .body("created_by", equalTo("wf-admin"));
    }

    @Test
    @TestSecurity(user = "wf-admin", roles = {"Admin"})
    void yamlChecks() {
        String[][] cases = {
                {yaml("bad_name", "1.0.0"), "document.name"},
                {"document: [oops", "yaml:"},
                {"do: []", "document: missing"},
                {yaml("hello", "1.0.0").replace("do:\n  - greet:", "steps:\n  - greet:"), "do:"},
                {yaml("hello", "1.0.0") + "use:\n  retries:\n    r: &r {delay: {seconds: 1}}\n  x: *r\n", "anchors"},
                {yaml("hello", "1.0.0") + "---\n" + yaml("other", "1.0.0"), "exactly one"},
        };
        for (String[] c : cases) {
            given().contentType(ContentType.JSON).body(Map.of("yaml", c[0]))
                    .post(PATH).then().statusCode(400).body("msg", containsString(c[1]));
        }
        given().contentType(ContentType.JSON).body(Map.of("yaml", yaml("hello", "2.0.0")))
                .post(PATH + "/validate").then().statusCode(200).body("document.version", equalTo("2.0.0"))
                .body("workflow.do[0].greet.set.message", containsString("Hello"));
    }

    @Test
    @TestSecurity(user = "wf-admin", roles = {"Admin"})
    void sameVersionTwiceIsAConflict() {
        create(yaml("hello", "1.0.0"));
        given().contentType(ContentType.JSON).body(Map.of("yaml", yaml("hello", "1.0.0")))
                .post(PATH).then().statusCode(409).body("msg", containsString("wftest:hello:1.0.0"));
        create(yaml("hello", "1.0.1"));
    }

    @Test
    @TestSecurity(user = "wf-manager", roles = {"Manager"})
    void deployMakesItImmutable() {
        String id = create(yaml("hello", "1.0.0"));
        given().contentType(ContentType.JSON).post(PATH + "/" + id + "/deploy").then().statusCode(200)
                .body("status", equalTo("DEPLOYED"))
                .body("deployed_by", equalTo("wf-manager"))
                .body("message", containsString("1.5"));
        assertEquals(1, calls.size(), calls.toString());
        assertTrue(calls.get(0).startsWith("PUT /workflows/wftest/hello/1.0.0 token=wf-test-token yaml=true"), calls.toString());

        given().contentType(ContentType.JSON).body(Map.of("yaml", yaml("hello", "1.0.0").replace("Hello,", "Hi,")))
                .put(PATH + "/" + id).then().statusCode(409).body("msg", containsString("new version"));
        given().contentType(ContentType.JSON).post(PATH + "/" + id + "/deploy").then().statusCode(409);

        given().delete(PATH + "/" + id).then().statusCode(200);
        assertTrue(calls.get(calls.size() - 1).startsWith("DELETE /workflows/wftest/hello/1.0.0 token=wf-test-token"), calls.toString());
    }

    @Test
    @TestSecurity(user = "wf-admin", roles = {"Admin"})
    void runnerRefusalMarksFailedAndKeepsItEditable() {
        String id = create(yaml("hello", "1.0.0"));
        putStatus = 422;
        putBody = "{\"error\": \"/do/0/greet: must be valid to one and only one schema \\u2014 0 are valid\"}";
        given().contentType(ContentType.JSON).post(PATH + "/" + id + "/deploy").then().statusCode(422)
                .body("msg", containsString("one and only one schema — 0"));
        given().get(PATH + "/" + id).then().body("status", equalTo("FAILED"))
                .body("message", containsString("one and only one schema"));

        // fixed YAML -> DRAFT again, message cleared
        given().contentType(ContentType.JSON).body(Map.of("yaml", yaml("hello", "1.0.0")))
                .put(PATH + "/" + id).then().statusCode(200);
        given().get(PATH + "/" + id).then().body("status", equalTo("DRAFT"));

        // bridge trouble (5xx) is not the workflow's fault: status stays DRAFT, the message says why
        putStatus = 504;
        putBody = "{\"error\": \"the runner did not register it within 20 s\"}";
        given().contentType(ContentType.JSON).post(PATH + "/" + id + "/deploy").then().statusCode(504);
        given().get(PATH + "/" + id).then().body("status", equalTo("DRAFT"))
                .body("message", containsString("did not register"));
    }

    @Test
    @TestSecurity(user = "wf-admin", roles = {"Admin"})
    void updateIgnoresClientSetStatus() {
        String id = create(yaml("hello", "1.0.0"));
        given().contentType(ContentType.JSON)
                .body(Map.of("yaml", yaml("hello", "1.0.0"), "status", "DEPLOYED", "deployed_by", "mallory"))
                .put(PATH + "/" + id).then().statusCode(200)
                // the answer is the whole row: the admin keeps it as the element (uuid needed for Deploy)
                .body("uuid", equalTo(id)).body("created_by", equalTo("wf-admin"));
        given().get(PATH + "/" + id).then().body("status", equalTo("DRAFT"));
    }

    @Test
    @TestSecurity(user = "wf-user", roles = {"User"})
    void plainUsersCannotWriteNorRead() {
        given().contentType(ContentType.JSON).body(Map.of("yaml", yaml("hello", "1.0.0")))
                .post(PATH).then().statusCode(403);
        given().get(PATH).then().statusCode(403);
    }

    @Test
    void anonymousCannotRead() {
        given().get(PATH).then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "wf-admin", roles = {"Admin"})
    void deployStatusComesFromTheBridge() {
        given().get(PATH + "/deploy-status").then().statusCode(200).body("writes", equalTo(true));
    }
}
