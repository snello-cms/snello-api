package io.snello.test.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.snello.api.service.JdbcRepository;
import io.snello.model.Metadata;
import io.snello.service.MetadataService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Regression: {@code select_fields} and {@code _sort} were spliced verbatim into the SQL text, so a user of a
 * protected table could read other owners' rows through a subquery, e.g.
 * {@code GET /api/lovers?select_fields=uuid,(SELECT string_agg(username||'='||name, ',') FROM lovers) AS leak}
 * returned the caller's single row carrying every owner's data.
 * <p>
 * Uses the dump's {@code lovers} table ({@code api_protected}, {@code username_field=username}).
 */
@QuarkusTest
class SqlFragmentInjectionTest {

    private static final String ALICE = "inj-alice";
    private static final String BOB = "inj-bob";
    private static final String BOB_SECRET = "INJ-BOB-SECRET";

    @Inject
    JdbcRepository jdbc;

    @Inject
    MetadataService metadataService;

    @Test
    @TestSecurity(user = ALICE, roles = {"User"})
    void subqueriesInSelectFieldsAndSortAreRejected() throws Exception {
        Metadata lovers = metadataService.metadataMap().get("lovers");
        Assumptions.assumeTrue(lovers != null && lovers.api_protected && "username".equals(lovers.username_field),
                "seed has no protected lovers metadata");
        String a = seedLover(ALICE, "inj-alice-lover");
        String b = seedLover(BOB, BOB_SECRET);
        try {
            String leak = "uuid, username, (SELECT string_agg(username || '=' || name, ',') FROM lovers) AS leak";
            // list and fetch both honour select_fields
            given().accept(ContentType.JSON).queryParam("select_fields", leak)
                    .when().get("/api/lovers").then().statusCode(400);
            given().accept(ContentType.JSON).queryParam("select_fields", leak)
                    .when().get("/api/lovers/{id}", a).then().statusCode(400);
            given().accept(ContentType.JSON)
                    .queryParam("_sort", "(CASE WHEN (SELECT count(*) FROM lovers WHERE name='" + BOB_SECRET + "')=1 THEN uuid END)")
                    .when().get("/api/lovers").then().statusCode(400);
            given().accept(ContentType.JSON).queryParam("_sort", "name; DROP TABLE lovers")
                    .when().get("/api/lovers").then().statusCode(400);

            // the legitimate shapes keep working, still owner-filtered
            List<Map<String, Object>> rows = given().accept(ContentType.JSON)
                    .queryParam("select_fields", "uuid, username, name AS lover_name")
                    .queryParam("_sort", "name:desc, uuid asc")
                    .queryParam("_limit", 100)
                    .when().get("/api/lovers").then().statusCode(200).extract().jsonPath().getList("");
            assertEquals(1, rows.size());
            assertEquals(ALICE, rows.get(0).get("username"));
            assertEquals("inj-alice-lover", rows.get(0).get("lover_name"));
            assertFalse(rows.toString().contains(BOB_SECRET));
            given().accept(ContentType.JSON).queryParam("_sort", "random")
                    .when().get("/api/lovers").then().statusCode(200);
        } finally {
            jdbc.delete("lovers", "uuid", a);
            jdbc.delete("lovers", "uuid", b);
        }
    }

    private String seedLover(String owner, String name) throws Exception {
        String id = UUID.randomUUID().toString();
        Map<String, Object> m = new HashMap<>();
        m.put("uuid", id);
        m.put("username", owner);
        m.put("name", name);
        jdbc.create("lovers", "uuid", m);
        return id;
    }
}
