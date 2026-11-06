package io.snello.test.api;

import io.quarkus.test.junit.QuarkusTest;
import io.snello.api.service.JdbcRepository;
import jakarta.inject.Inject;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.snello.model.Metadata;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression: GET /api/{table} on an {@code api_protected} table used to build the owner filter and then
 * discard it, returning every user's rows to any authenticated caller.
 */
@QuarkusTest
class ApiRowLevelSecurityTest {

    private static final String CALLER = "row-filter-test-user";
    private static final String OTHER_OWNER = "row-filter-other-owner";

    @Inject
    JdbcRepository jdbcRepository;

    @Test
    @TestSecurity(user = CALLER, roles = {"User"})
    void listOnProtectedTableReturnsOnlyCallersRows() throws Exception {
        Metadata protectedMeta = findProtectedMetadata();
        // seed a row owned by someone else, straight through JDBC (bypasses API security and actions)
        String key = protectedMeta.table_key == null ? "uuid" : protectedMeta.table_key;
        String id = UUID.randomUUID().toString();
        Map<String, Object> foreign = new HashMap<>();
        foreign.put(key, id);
        foreign.put(protectedMeta.username_field, OTHER_OWNER);
        try {
            jdbcRepository.create(protectedMeta.table_name, key, foreign);
        } catch (Exception e) {
            Assumptions.abort("cannot seed a minimal row into " + protectedMeta.table_name + ": " + e.getMessage());
        }
        try {
            assertOnlyCallerRows(protectedMeta);
        } finally {
            jdbcRepository.delete(protectedMeta.table_name, key, id);
        }
    }

    private void assertOnlyCallerRows(Metadata protectedMeta) {
        List<Map<String, Object>> rows = given()
                .accept(ContentType.JSON)
                .queryParam("_limit", 100)
                .when()
                .get("/api/{table}", protectedMeta.table_name)
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("");
        for (Map<String, Object> row : rows) {
            assertEquals(CALLER, row.get(protectedMeta.username_field),
                    "row of another owner leaked from " + protectedMeta.table_name);
        }
    }

    @Test
    void listOnProtectedTableRequiresAuthentication() {
        Metadata protectedMeta = findProtectedMetadata();
        given()
                .accept(ContentType.JSON)
                .when()
                .get("/api/{table}", protectedMeta.table_name)
                .then()
                .statusCode(401);
    }

    private static Metadata findProtectedMetadata() {
        List<Metadata> metadatas = given()
                .accept(ContentType.JSON)
                .queryParam("_limit", 500)
                .when()
                .get("/api/metadatas")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("", Metadata.class);
        Metadata found = metadatas.stream()
                .filter(m -> m.api_protected && m.username_field != null && !m.username_field.isBlank())
                .findFirst()
                .orElse(null);
        Assumptions.assumeTrue(found != null, "seed has no api_protected metadata with username_field");
        return found;
    }
}
