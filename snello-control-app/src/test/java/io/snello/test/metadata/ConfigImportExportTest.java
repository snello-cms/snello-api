package io.snello.test.metadata;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.snello.api.service.JdbcRepository;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/** Actions, conditions, select queries, scheduled actions and print templates in the metadata export/import. */
@QuarkusTest
@TestSecurity(user = "test-admin", roles = {"Admin"})
class ConfigImportExportTest {

    private static final String TABLE = "cfg_xfer_table";
    private static final String OTHER_TABLE = "cfg_xfer_other";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Inject
    JdbcRepository jdbc;

    private String metadataUuid;

    @BeforeEach
    void seed() throws Exception {
        clean();
        metadataUuid = given().contentType(ContentType.JSON)
                .body(Map.of("table_name", TABLE, "table_key", "uuid", "table_key_type", "uuid",
                        "description", "config transfer", "api_protected", false, "already_exist", false,
                        "created", false))
                .post("/api/metadatas").then().statusCode(200).extract().path("uuid");

        insert("actions", Map.of("uuid", "cfg-act-1", "name", "cfg-act", "description", "d",
                "metadata_name", TABLE, "phase", "POST", "body", "log.info('a');"));
        insert("actions", Map.of("uuid", "cfg-act-2", "name", "cfg-act-other", "description", "d",
                "metadata_name", OTHER_TABLE, "phase", "POST", "body", "log.info('b');"));
        insert("conditions", Map.of("uuid", "cfg-cond-1", "metadata_uuid", metadataUuid, "metadata_name", TABLE,
                "condition", "x_nn", "query_params", "", "sub_query", "x is not null",
                "metadata_multijoin_uuid", ""));
        insert("selectqueries", Map.of("uuid", "cfg-sq-1", "query_name", "cfg_xfer_q", "with_params", true,
                "select_query", "SELECT 'mario' AS username", "api_protected", true, "username_field", "username"));
        insert("scheduledactions", Map.of("uuid", "cfg-job-1", "name", "test-cfg-job", "frequency", "0 17 * * *",
                "enabled", true, "body", "log.info('job');", "next_run", "2030-01-01T17:00+01:00",
                "last_status", "OK"));
        insert("printtemplates", Map.of("uuid", "cfg-pt-1", "name", "cfg-xfer-print", "export_type", "csv",
                "sql_query", "SELECT 1 AS n", "api_protected", false));
    }

    @AfterEach
    void clean() throws Exception {
        jdbc.executeQuery("DELETE FROM actions WHERE name LIKE 'cfg-act%'");
        jdbc.executeQuery("DELETE FROM conditions WHERE metadata_name = '" + TABLE + "'");
        jdbc.executeQuery("DELETE FROM selectqueries WHERE query_name LIKE 'cfg_xfer%'");
        jdbc.executeQuery("DELETE FROM scheduledactions WHERE name LIKE 'test-cfg-%'");
        jdbc.executeQuery("DELETE FROM printtemplates WHERE name LIKE 'cfg-xfer%'");
        jdbc.executeQuery("DELETE FROM fielddefinitions WHERE metadata_name = '" + TABLE + "'");
        jdbc.executeQuery("DELETE FROM metadatas WHERE table_name = '" + TABLE + "'");
    }

    private void insert(String table, Map<String, Object> row) throws Exception {
        jdbc.create(table, "uuid", new HashMap<>(row));
    }

    private static Map<String, Object> allFlags(List<String> metadatas) {
        Map<String, Object> body = new HashMap<>();
        body.put("metadatas", metadatas);
        body.put("actions", true);
        body.put("conditions", true);
        body.put("select_queries", true);
        body.put("scheduled_actions", true);
        body.put("print_templates", true);
        return body;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> exportConfig(Map<String, Object> body) {
        Map<String, Object> out = given().contentType(ContentType.JSON).body(body)
                .post("/api/metadatas/export").then().statusCode(200).extract().jsonPath().getMap("$");
        return (Map<String, Object>) out.get("config");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> config, String table) {
        return (List<Map<String, Object>>) config.get(table);
    }

    @Test
    void exportCarriesRequestedConfigAndActionsFollowTheMetadatas() {
        Map<String, Object> config = exportConfig(allFlags(List.of(metadataUuid)));
        assertNotNull(config, "config section expected");

        List<Map<String, Object>> actions = rows(config, "actions");
        assertTrue(actions.stream().anyMatch(a -> "cfg-act".equals(a.get("name"))));
        assertTrue(actions.stream().noneMatch(a -> "cfg-act-other".equals(a.get("name"))),
                "actions of metadatas not exported must stay out");
        assertTrue(rows(config, "conditions").stream().anyMatch(c -> "cfg-cond-1".equals(c.get("uuid"))));
        assertTrue(rows(config, "selectqueries").stream().anyMatch(q -> "cfg_xfer_q".equals(q.get("query_name"))));
        Map<String, Object> job = rows(config, "scheduledactions").stream()
                .filter(j -> "test-cfg-job".equals(j.get("name"))).findFirst().orElseThrow();
        assertFalse(job.containsKey("next_run"), "scheduler state is not exported");
        assertFalse(job.containsKey("last_status"));
        assertTrue(rows(config, "printtemplates").stream().anyMatch(p -> "cfg-xfer-print".equals(p.get("name"))));
    }

    @Test
    void exportWithoutFlagsHasNoConfigAndFlagsAloneNeedNoMetadata() {
        assertNull(exportConfig(Map.of("metadatas", List.of(metadataUuid))));

        Map<String, Object> onlyTemplates = exportConfig(Map.of("metadatas", List.of(), "print_templates", true));
        assertEquals(List.of("printtemplates"), List.copyOf(onlyTemplates.keySet()));

        given().contentType(ContentType.JSON).body(Map.of("metadatas", List.of()))
                .post("/api/metadatas/export").then().statusCode(400);
    }

    @Test
    void importCreatesMissingOverwritesExistingAndDisablesJobs() throws Exception {
        Map<String, Object> config = exportConfig(allFlags(List.of(metadataUuid)));

        // target state: query and template gone, job edited, action edited
        jdbc.executeQuery("DELETE FROM selectqueries WHERE query_name = 'cfg_xfer_q'");
        jdbc.executeQuery("DELETE FROM printtemplates WHERE name = 'cfg-xfer-print'");
        jdbc.executeQuery("UPDATE scheduledactions SET body = 'changed' WHERE name = 'test-cfg-job'");
        jdbc.executeQuery("UPDATE actions SET body = 'changed' WHERE name = 'cfg-act'");

        Map<String, Object> result = given().contentType(ContentType.JSON)
                .body(Map.of("metadatas", List.of(), "config", config))
                .post("/api/metadatas/import").then().statusCode(200).extract().jsonPath().getMap("config");
        assertResult(result, "selectqueries", 1, 0);
        assertResult(result, "printtemplates", 1, 0);
        assertResult(result, "scheduledactions", 0, 1);
        assertResult(result, "actions", 0, 1); // only cfg-act was exported, and it exists
        assertResult(result, "conditions", 0, 1);

        Map<String, Object> job = jdbc.fetch(null, "scheduledactions", "uuid", "cfg-job-1");
        assertEquals("log.info('job');", job.get("body"));
        assertEquals(Boolean.FALSE, job.get("enabled"), "imported jobs are disabled");
        assertNull(job.get("next_run"), "a disabled job has no next run");
        assertEquals("log.info('a');", jdbc.fetch(null, "actions", "uuid", "cfg-act-1").get("body"));
        assertEquals("cfg-sq-1", jdbc.fetch(null, "selectqueries", "uuid", "cfg-sq-1").get("uuid"),
                "a free exported uuid is kept");

        // the re-imported protected select query is live (cache reloaded)
        given().get("/api/cfg_xfer_q").then().statusCode(200);
    }

    @Test
    void invalidRowsAreReportedNotThrown() {
        Map<String, Object> config = Map.of(
                "conditions", List.of(Map.of("uuid", "cfg-cond-x", "metadata_name", "cfg_missing_table",
                        "condition", "c", "sub_query", "1=1")),
                "scheduledactions", List.of(Map.of("name", "test-cfg-bad", "frequency", "every day")),
                "printtemplates", List.of(Map.of("name", "cfg-xfer bad", "export_type", "csv")));
        Map<String, Object> result = given().contentType(ContentType.JSON)
                .body(Map.of("config", config))
                .post("/api/metadatas/import").then().statusCode(200).extract().jsonPath().getMap("config");
        for (String table : List.of("conditions", "scheduledactions", "printtemplates")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> r = (Map<String, Object>) result.get(table);
            assertEquals(0, r.get("created"), table);
            assertEquals(1, ((List<?>) r.get("errors")).size(), table + " " + r);
        }
    }

    @Test
    void zipRoundTripCarriesConfig() throws Exception {
        Map<String, Object> body = allFlags(List.of(metadataUuid));
        body.put("format", "zip");
        byte[] zip = given().contentType(ContentType.JSON).body(body)
                .post("/api/metadatas/export").then().statusCode(200).extract().asByteArray();

        Map<String, Object> manifest = null;
        Map<String, Object> metadatas = null;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = zis.getNextEntry()) != null; ) {
                byte[] bytes = zis.readAllBytes();
                if (e.getName().equals("manifest.json")) {
                    manifest = MAPPER.readValue(bytes, Map.class);
                } else if (e.getName().equals("metadatas.json")) {
                    metadatas = MAPPER.readValue(bytes, Map.class);
                }
            }
        }
        assertNotNull(manifest);
        assertEquals(1, ((Map<?, ?>) manifest.get("config")).get("printtemplates"));
        assertNotNull(metadatas.get("config"));

        // the metadata exists here (409): round-trip a config-only zip through the raw zip import
        byte[] configZip = given().contentType(ContentType.JSON)
                .body(Map.of("metadatas", List.of(), "print_templates", true, "format", "zip"))
                .post("/api/metadatas/export").then().statusCode(200).extract().asByteArray();
        jdbc.executeQuery("DELETE FROM printtemplates WHERE name = 'cfg-xfer-print'");
        Map<String, Object> result = given().contentType("application/zip").body(configZip)
                .post("/api/metadatas/import").then().statusCode(200).extract().jsonPath().getMap("config");
        assertResult(result, "printtemplates", 1, 0);
    }

    @SuppressWarnings("unchecked")
    private static void assertResult(Map<String, Object> result, String table, int created, int minUpdated) {
        Map<String, Object> r = (Map<String, Object>) result.get(table);
        assertNotNull(r, table + " missing in " + result);
        assertEquals(created, r.get("created"), table + " created " + r);
        // select queries, jobs and templates export every row: other seeded rows come back as updates
        assertTrue((Integer) r.get("updated") >= minUpdated, table + " updated " + r);
        assertTrue(((List<?>) r.get("errors")).isEmpty(), table + " errors " + r);
    }
}
