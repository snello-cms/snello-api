package io.snello.test.flow;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.junit.QuarkusTest;
import io.snello.api.service.JdbcRepository;
import io.snello.service.flow.WorkflowSeedService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Startup import of the snello-flows workflows into the editor: a version the runner already runs is adopted, a new
 * one is deployed, one refused by the runner is kept as FAILED, ui-*.yaml is skipped, and a second run imports
 * nothing (only new namespace:name:version). Fake flow-bridge + runner on 18391.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkflowSeedTest {

    @Inject
    WorkflowSeedService seed;

    @Inject
    JdbcRepository jdbc;

    HttpServer fake;
    final List<String> calls = new CopyOnWriteArrayList<>();

    @BeforeAll
    void start() throws Exception {
        fake = HttpServer.create(new InetSocketAddress("127.0.0.1", 18391), 0);
        fake.createContext("/workflows", ex -> {
            String m = ex.getRequestMethod();
            String p = ex.getRequestURI().getPath();
            ex.getRequestBody().readAllBytes();
            calls.add(m + " " + p);
            if ("GET".equals(m)) {
                reply(ex, 200, "{\"writes\": true, \"workflows\": []}");
            } else if (p.endsWith("/adopt")) {
                reply(ex, 200, "{\"file\": \"ui-x.yaml\", \"removed\": [\"seedtest-running.yaml\"]}");
            } else if (p.contains("/broken/")) {
                reply(ex, 422, "{\"error\": \"the runner refused it: bad task\"}");
            } else {
                reply(ex, 201, "{\"seconds\": 2}");
            }
        });
        fake.createContext("/runner/q/flow/definitions/", ex -> {
            calls.add("RUNNER " + ex.getRequestURI().getPath());
            boolean running = ex.getRequestURI().getPath().contains("/running/");
            reply(ex, running ? 200 : 404, running ? "{}" : "{\"error\":\"not found\"}");
        });
        fake.start();
        jdbc.executeQuery("DELETE FROM workflowdefinitions WHERE namespace = 'seedtest'");
    }

    @AfterAll
    void stop() throws Exception {
        fake.stop(0);
        jdbc.executeQuery("DELETE FROM workflowdefinitions WHERE namespace = 'seedtest'");
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws java.io.IOException {
        byte[] out = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, out.length);
        ex.getResponseBody().write(out);
        ex.close();
    }

    private static String yaml(String name) {
        return WorkflowDefinitionsTest.yaml(name, "1.0.0").replace("namespace: wftest", "namespace: seedtest");
    }

    @Test
    void importsOnlyNewWorkflows(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("seedtest-running.yaml"), yaml("running"));
        Files.writeString(dir.resolve("seedtest-fresh.yaml"), yaml("fresh"));
        Files.writeString(dir.resolve("seedtest-broken.yml"), yaml("broken"));
        Files.writeString(dir.resolve("ui-seedtest-skipped-1.0.0.yaml"), yaml("skipped"));
        Files.writeString(dir.resolve("README.md"), "not a workflow");

        assertEquals(3, seed.run(dir));
        assertTrue(calls.contains("POST /workflows/seedtest/running/1.0.0/adopt"), calls.toString());
        assertTrue(calls.contains("PUT /workflows/seedtest/fresh/1.0.0"), calls.toString());
        assertTrue(calls.stream().noneMatch(c -> c.contains("skipped")), calls.toString());

        List<Map<String, Object>> rows = jdbc.list(
                "SELECT name, status, message, created_by, yaml FROM workflowdefinitions WHERE namespace = 'seedtest' ORDER BY name");
        assertEquals(3, rows.size(), rows.toString());
        Map<String, Object> broken = rows.get(0), fresh = rows.get(1), running = rows.get(2);
        assertEquals("FAILED", broken.get("status"));
        assertTrue(String.valueOf(broken.get("message")).contains("bad task"), broken.toString());
        assertEquals("DEPLOYED", fresh.get("status"));
        assertTrue(String.valueOf(fresh.get("message")).contains("loaded by the runner in 2"), fresh.toString());
        assertEquals("DEPLOYED", running.get("status"));
        assertTrue(String.valueOf(running.get("message")).contains("already running"), running.toString());
        assertEquals("snello-flows", running.get("created_by"));
        assertTrue(String.valueOf(running.get("yaml")).contains("name: running"));

        // second start: everything is in the editor already -> nothing imported, no deploy
        calls.clear();
        assertEquals(0, seed.run(dir));
        assertTrue(calls.stream().noneMatch(c -> c.startsWith("PUT") || c.startsWith("POST")), calls.toString());
    }
}
