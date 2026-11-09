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

/**
 * Regression: a matching metadata condition replaced the generic request filters, including the owner filter of
 * a protected table, so a user received other owners' rows. Uses the dump's protected {@code lovers} table.
 */
@QuarkusTest
class ConditionRowSecurityTest {

    private static final String ME = "cond-me";
    private static final String OTHER = "cond-other";

    @Inject
    JdbcRepository jdbc;

    @Inject
    MetadataService metadataService;

    private void lover(String key, String owner, String city) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("uuid", key);
        m.put("username", owner);
        m.put("name", key);
        m.put("city", city);
        jdbc.create("lovers", "uuid", m);
    }

    private String condition(String trigger, String params, String subQuery) throws Exception {
        Metadata lovers = metadataService.metadataMap().get("lovers");
        String id = java.util.UUID.randomUUID().toString();
        Map<String, Object> c = new HashMap<>();
        c.put("uuid", id);
        c.put("metadata_uuid", lovers.uuid);
        c.put("metadata_name", "lovers");
        c.put("metadata_multijoin_uuid", "");
        c.put("condition", trigger);
        c.put("query_params", params);
        c.put("sub_query", subQuery);
        c.put("separator", "AND");
        jdbc.create("conditions", "uuid", c);
        metadataService.invalidateSecondaryCaches();
        return id;
    }

    private List<String> listKeys(String query) {
        return given().accept(ContentType.JSON).when().get("/api/lovers?_limit=100&" + query)
                .then().statusCode(200).extract().jsonPath().getList("uuid", String.class)
                .stream().filter(k -> k.startsWith("cond-")).sorted().toList();
    }

    @Test
    @TestSecurity(user = ME, roles = {"User"})
    void matchingConditionKeepsTheOwnerFilter() throws Exception {
        Metadata lovers = metadataService.metadataMap().get("lovers");
        Assumptions.assumeTrue(lovers != null && lovers.api_protected && "username".equals(lovers.username_field));
        lover("cond-mine", ME, "Pisa");
        lover("cond-theirs", OTHER, "Pisa");
        // the OR inside the sub_query must not escape the owner predicate either
        String id = condition("in_city_nn&&any_nn", "in_city", "(city = ? OR 1 = 1)");
        try {
            assertEquals(List.of("cond-mine"), listKeys("in_city=Pisa&any=1"));
            assertEquals(List.of("cond-mine"), listKeys(""));
        } finally {
            jdbc.executeQuery("DELETE FROM conditions WHERE uuid = '" + id + "'");
            jdbc.delete("lovers", "uuid", "cond-mine");
            jdbc.delete("lovers", "uuid", "cond-theirs");
            metadataService.invalidateSecondaryCaches();
        }
    }
}
