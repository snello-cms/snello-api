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

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressions on {@code PUT /api/{table}/{key}} for the dump's {@code lovers} table (api_protected, owner column
 * {@code username}, slug key derived from {@code username}):
 * <ul>
 *     <li>a non-admin could PUT another user's row: it was overwritten and re-stamped with the caller's username;</li>
 *     <li>an update re-derived the slug key and moved the row (mario-1 -> mario-2, or any legacy key -> its slug),
 *         so the admin's reload and every reference to the old key broke. The key must never change on update.</li>
 * </ul>
 */
@QuarkusTest
class ApiWriteOwnershipTest {

    private static final String OWNER = "wrowner"; // no hyphen: createSlug drops it, the slug must equal the name
    private static final String OTHER = "wr-other";

    @Inject
    JdbcRepository jdbc;

    @Inject
    MetadataService metadataService;

    private void assumeLoversSeed() throws Exception {
        Metadata lovers = metadataService.metadataMap().get("lovers");
        Assumptions.assumeTrue(lovers != null && lovers.api_protected && "username".equals(lovers.username_field)
                && "slug".equals(lovers.table_key_type) && "username".equals(lovers.table_key_addition),
                "seed has no protected, username-slugged lovers metadata");
    }

    private void seed(String key, String owner, String name) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("uuid", key);
        m.put("username", owner);
        m.put("name", name);
        jdbc.create("lovers", "uuid", m);
    }

    private Map<String, Object> row(String key) throws Exception {
        List<Map<String, Object>> rows = jdbc.list("SELECT uuid, name, username FROM lovers WHERE uuid = '" + key + "'");
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static Map<String, Object> body(String name, String username) {
        Map<String, Object> b = new HashMap<>();
        b.put("name", name);
        b.put("username", username);
        return b;
    }

    @Test
    @TestSecurity(user = OWNER, roles = {"User"})
    void userCannotOverwriteAnotherUsersRow() throws Exception {
        assumeLoversSeed();
        seed("wr-other-row", OTHER, "private");
        try {
            given().contentType(ContentType.JSON).body(body("overwritten", OTHER))
                    .when().put("/api/lovers/{id}", "wr-other-row").then().statusCode(403);
            Map<String, Object> after = row("wr-other-row");
            assertEquals("private", after.get("name"));
            assertEquals(OTHER, after.get("username"));
            given().contentType(ContentType.JSON).body(body("x", OWNER))
                    .when().put("/api/lovers/{id}", "wr-missing-row").then().statusCode(404);
        } finally {
            jdbc.delete("lovers", "uuid", "wr-other-row");
        }
    }

    @Test
    @TestSecurity(user = OWNER, roles = {"User"})
    void userUpdateNeverChangesTheKey() throws Exception {
        assumeLoversSeed();
        // "wrowner-1" is a collision-suffixed slug; "wr-legacy-key" is not the slug of its owner at all
        seed(OWNER, OWNER, "first");
        seed(OWNER + "-1", OWNER, "second");
        seed("wr-legacy-key", OWNER, "legacy");
        try {
            for (String key : List.of(OWNER + "-1", "wr-legacy-key")) {
                for (String name : List.of("edited", "edited again")) {
                    given().contentType(ContentType.JSON).body(body(name, OWNER))
                            .when().put("/api/lovers/{id}", key).then().statusCode(200);
                    Map<String, Object> after = row(key);
                    assertTrue(after != null, "row moved away from its key " + key);
                    assertEquals(name, after.get("name"));
                }
            }
            // a key in the body is ignored
            Map<String, Object> withKey = body("body key ignored", OWNER);
            withKey.put("uuid", "wr-hijacked-key");
            given().contentType(ContentType.JSON).body(withKey)
                    .when().put("/api/lovers/{id}", "wr-legacy-key").then().statusCode(200);
            assertEquals("body key ignored", row("wr-legacy-key").get("name"));
            assertEquals(null, row("wr-hijacked-key"));
            assertEquals(null, row(OWNER + "-2"), "update must not generate a new slug key");
        } finally {
            for (String key : List.of(OWNER, OWNER + "-1", OWNER + "-2", "wr-legacy-key", "wr-hijacked-key")) {
                jdbc.delete("lovers", "uuid", key);
            }
        }
    }

    @Test
    @TestSecurity(user = "wr-admin", roles = {"Admin"})
    void adminChangingTheSlugSourceKeepsTheKey() throws Exception {
        assumeLoversSeed();
        seed("wr-admin-row", OWNER, "owned by wrowner");
        try {
            // username is the slug source of lovers: before, changing it renamed the row
            given().contentType(ContentType.JSON).body(body("moved to other owner", OTHER))
                    .when().put("/api/lovers/{id}", "wr-admin-row").then().statusCode(200);
            Map<String, Object> after = row("wr-admin-row");
            assertTrue(after != null, "admin update moved the row to a new key");
            assertEquals(OTHER, after.get("username"));
        } finally {
            jdbc.delete("lovers", "uuid", "wr-admin-row");
        }
    }
}
