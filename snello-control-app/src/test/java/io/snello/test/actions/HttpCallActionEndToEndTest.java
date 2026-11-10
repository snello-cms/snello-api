package io.snello.test.actions;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.snello.api.service.JdbcRepository;
import io.snello.service.MetadataService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Fiorenzo's request, verbatim syntax, real path: action row in `actions` -> REST insert -> interceptor -> script:
 * <pre>
 * call(GET, "https://url.to/call").result();
 * call(POST,"https://url.to/call", object).result();
 * </pre>
 */
@QuarkusTest
@TestSecurity(user = "http-admin", roles = {"Admin"})
class HttpCallActionEndToEndTest {

    private static final String TABLE = "http_probe";
    private static final String META = "http-probe-meta";

    @Inject
    JdbcRepository jdbc;
    @Inject
    MetadataService metadataService;

    HttpServer server;
    String base;
    final List<String> received = new CopyOnWriteArrayList<>();

    @BeforeEach
    void seed() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/call", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(ex.getRequestMethod() + " " + body);
            byte[] b = ("{\"method\":\"" + ex.getRequestMethod() + "\",\"score\":42}").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();

        cleanup();
        jdbc.executeQuery("CREATE TABLE " + TABLE + " (uuid varchar(50) PRIMARY KEY, name varchar(50), notes varchar(300))");
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
        if (received.isEmpty() && server == null) {
            return;
        }
        jdbc.executeQuery("DROP TABLE IF EXISTS " + TABLE);
        jdbc.executeQuery("DELETE FROM metadatas WHERE uuid = '" + META + "'");
        jdbc.executeQuery("DELETE FROM actions WHERE metadata_name = '" + TABLE + "'");
        metadataService.invalidateMetadataCaches();
        metadataService.invalidateSecondaryCaches();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void action(String phase, String body) throws Exception {
        Map<String, Object> a = new HashMap<>();
        a.put("uuid", java.util.UUID.randomUUID().toString());
        a.put("name", "http-" + phase);
        a.put("description", "test");
        a.put("metadata_name", TABLE);
        a.put("condition", "PERSIST");
        a.put("phase", phase);
        a.put("body", body);
        jdbc.create("actions", "uuid", a);
        metadataService.invalidateSecondaryCaches();
    }

    @Test
    void preActionUsesExactRequestedSyntax() throws Exception {
        // Fiorenzo's two lines, only the url swapped for the local server; PRE so we can write the outcome into the row
        action("PRE", ""
                + "var g = call(GET, \"" + base + "/call\").result();\n"
                + "var object = {name: map.get('name'), source: 'snello'};\n"
                + "var p = call(POST,\"" + base + "/call\", object).result();\n"
                + "map.put('notes', g.method + ' ' + g.score + ' / ' + p.method + ' ' + p.score);");

        String id = given().contentType(ContentType.JSON).body(Map.of("name", "Mario")).when().post("/api/" + TABLE)
                .then().statusCode(200).extract().path("uuid");

        String notes = (String) jdbc.list("SELECT notes FROM " + TABLE + " WHERE uuid = '" + id + "'").get(0).get("notes");
        assertEquals("GET 42 / POST 42", notes);
        assertEquals(2, received.size(), received.toString());
        assertEquals("GET ", received.get(0));
        assertTrue(received.get(1).startsWith("POST {"), received.get(1));
        assertTrue(received.get(1).contains("\"name\":\"Mario\"") && received.get(1).contains("\"source\":\"snello\""),
                received.get(1));
    }

    @Test
    void postActionCallsExternalApiAsynchronously() throws Exception {
        action("POST", "call(POST,\"" + base + "/call\", {renter: map.get('name')}).result();");
        given().contentType(ContentType.JSON).body(Map.of("name", "Luigi")).when().post("/api/" + TABLE)
                .then().statusCode(200);
        for (int i = 0; i < 50 && received.isEmpty(); i++) {
            Thread.sleep(100);
        }
        assertEquals(1, received.size());
        assertTrue(received.get(0).contains("\"renter\":\"Luigi\""), received.get(0));
    }
}
