package io.snello.test.actions;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.junit.QuarkusTest;
import io.snello.model.Action;
import io.snello.service.ScriptService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** {@code call(GET, url).result()} and {@code call(POST, url, obj).result()} from inside an action script. */
@QuarkusTest
class HttpCallScriptTest {

    @Inject
    ScriptService scriptService;

    HttpServer server;
    String base;
    final AtomicReference<String> lastBody = new AtomicReference<>();
    final AtomicReference<String> lastAuth = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hello", ex -> reply(ex, 200, "{\"greeting\":\"ciao\",\"n\":3}"));
        server.createContext("/echo", ex -> {
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            reply(ex, 201, "{\"received\":true}");
        });
        server.createContext("/text", ex -> reply(ex, 200, "plain words"));
        server.createContext("/list", ex -> reply(ex, 200, "[{\"id\":1,\"name\":\"a\"},{\"id\":2,\"name\":\"b\"}]"));
        server.createContext("/boom", ex -> reply(ex, 500, "{\"error\":\"nope\"}"));
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int code, String body) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    private Map<String, Object> run(String js) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("base", base);
        Action a = new Action();
        a.name = "http-test";
        scriptService.execute(js, a, "t", "uuid", "k1", map);
        return map;
    }

    @Test
    void getReturnsParsedJson() throws Exception {
        Map<String, Object> m = run("var r = call(GET, map.get('base') + '/hello').result();"
                + "map.put('greeting', r.greeting); map.put('n', r.n);");
        assertEquals("ciao", m.get("greeting"));
        assertEquals(3, ((Number) m.get("n")).intValue());
    }

    @Test
    void postSendsObjectAsJsonWithHeaders() throws Exception {
        Map<String, Object> m = run("var res = call(POST, map.get('base') + '/echo', {name: 'Mario', age: 30},"
                + " {'Authorization': 'Bearer abc'});"
                + "map.put('status', res.status()); map.put('ok', res.ok()); map.put('received', res.result().received);");
        assertEquals(201, m.get("status"));
        assertEquals(true, m.get("ok"));
        assertEquals(true, m.get("received"));
        assertEquals("Bearer abc", lastAuth.get());
        assertTrue(lastBody.get().contains("\"name\":\"Mario\"") && lastBody.get().contains("\"age\":30"), lastBody.get());
    }

    @Test
    void nonJsonBodyComesBackAsString() throws Exception {
        assertEquals("plain words", run("map.put('t', call(GET, map.get('base') + '/text').result());").get("t"));
    }

    @Test
    void non2xxDoesNotThrow() throws Exception {
        Map<String, Object> m = run("var r = call(GET, map.get('base') + '/boom'); map.put('status', r.status()); map.put('ok', r.ok());");
        assertEquals(500, m.get("status"));
        assertEquals(false, m.get("ok"));
    }

    @Test
    void connectionFailureIsReportedNotThrown() throws Exception {
        Map<String, Object> m = run("var r = call(GET, 'http://127.0.0.1:1/x'); map.put('status', r.status()); map.put('err', r.error());");
        assertEquals(0, m.get("status"));
        assertNotNull(m.get("err"));
    }

    @Test
    void nestedArraysInBodyAreSentAsJsonArrays() throws Exception {
        run("call(POST, map.get('base') + '/echo', {name: 'x', items: [1, 2], sub: {tags: ['a', 'b']}, rows: [{id: 1}]});");
        assertEquals("{\"name\":\"x\",\"items\":[1,2],\"sub\":{\"tags\":[\"a\",\"b\"]},\"rows\":[{\"id\":1}]}", lastBody.get());
    }

    @Test
    void topLevelArrayBodyIsSentAsJsonArray() throws Exception {
        run("call(POST, map.get('base') + '/echo', [1, 'two', {three: 3}]);");
        assertEquals("[1,\"two\",{\"three\":3}]", lastBody.get());
    }

    @Test
    void javaMapBodyIsSentAsJson() throws Exception {
        // the common case: forward the row the action fires for
        Map<String, Object> m = run("map.put('name', 'Mario'); call(POST, map.get('base') + '/echo', map);");
        assertEquals("Mario", m.get("name"));
        assertTrue(lastBody.get().contains("\"name\":\"Mario\""), lastBody.get());
    }

    @Test
    void resultIsAPlainJsObjectAndArray() throws Exception {
        Map<String, Object> m = run("var list = call(GET, map.get('base') + '/list').result();"
                + "map.put('len', list.length);"
                + "map.put('names', list.map(function(x){ return x.name; }).join(','));"
                + "map.put('big', list.filter(function(x){ return x.id > 1; }).length);"
                + "map.put('json', JSON.stringify(call(GET, map.get('base') + '/hello').result()));");
        assertEquals(2, ((Number) m.get("len")).intValue());
        assertEquals("a,b", m.get("names"));
        assertEquals(1, ((Number) m.get("big")).intValue());
        assertEquals("{\"greeting\":\"ciao\",\"n\":3}", m.get("json"));
    }

    @Test
    void scriptCanLogWithJbossLogger() throws Exception {
        Map<String, Object> m = run("log.info('plain message'); log.infof('renter %s status %s', 'Mario', 200);"
                + "log.warn('careful'); log.debug('hidden at INFO'); map.put('logged', true);");
        assertEquals(true, m.get("logged"));
    }
}
