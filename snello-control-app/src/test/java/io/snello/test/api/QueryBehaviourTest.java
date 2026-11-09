package io.snello.test.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.snello.api.service.JdbcRepository;
import io.snello.service.MetadataService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regressions from the end-to-end review (conditions, select queries, error handling), on a throwaway table:
 * <ul>
 *     <li>a condition with a single trigger (no {@code &&} / {@code ||}) always threw and was ignored;</li>
 *     <li>SQL errors on lists were swallowed and answered as {@code 200 []};</li>
 *     <li>filters / sort on a select query ending with GROUP BY produced "... GROUP BY x WHERE ..." (syntax error);</li>
 *     <li>select query totals were a stub returning 0 on PostgreSQL / H2; queries without with_params ignored paging.</li>
 * </ul>
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueryBehaviourTest {

    private static final String TABLE = "qb_boats";
    private static final String META = "qb-boats-meta";

    @Inject
    JdbcRepository jdbc;

    @Inject
    MetadataService metadataService;

    @BeforeAll
    void seed() throws Exception {
        cleanup();
        jdbc.executeQuery("CREATE TABLE " + TABLE + " (uuid varchar(50) PRIMARY KEY, name varchar(50), hull varchar(20), len integer)");
        jdbc.executeQuery("INSERT INTO " + TABLE + " VALUES ('b1','one','kayak',3), ('b2','two','kayak',5), "
                + "('b3','three','canoe',6), ('b4','four','surfski',7), ('b5','five','surfski',4)");
        Map<String, Object> m = new HashMap<>();
        m.put("uuid", META);
        m.put("table_name", TABLE);
        m.put("table_key", "uuid");
        m.put("table_key_type", "uuid");
        m.put("created", true);
        jdbc.create("metadatas", "uuid", m);
        Map<String, Object> c = new HashMap<>();
        c.put("uuid", "qb-cond-min-len");
        c.put("metadata_uuid", META);
        c.put("metadata_name", TABLE);
        c.put("metadata_multijoin_uuid", "");
        c.put("condition", "min_len_nn"); // single trigger
        c.put("query_params", "min_len");
        c.put("sub_query", "len >= CAST(? AS integer)");
        c.put("separator", "AND");
        jdbc.create("conditions", "uuid", c);
        selectQuery("qb_by_hull", true, "SELECT hull, COUNT(*) AS boats FROM " + TABLE + " GROUP BY hull",
                "SELECT COUNT(DISTINCT hull) AS size_of FROM " + TABLE);
        selectQuery("qb_all", false, "SELECT uuid, name FROM " + TABLE + " ORDER BY name", null);
        metadataService.invalidateMetadataCaches();
        metadataService.invalidateSecondaryCaches();
    }

    private void selectQuery(String name, boolean withParams, String sql, String count) throws Exception {
        Map<String, Object> q = new HashMap<>();
        q.put("uuid", "qb-" + name);
        q.put("query_name", name);
        q.put("with_params", withParams);
        q.put("select_query", sql);
        q.put("select_query_count", count);
        jdbc.create("selectqueries", "uuid", q);
    }

    @AfterAll
    void cleanup() throws Exception {
        jdbc.executeQuery("DROP TABLE IF EXISTS " + TABLE);
        jdbc.executeQuery("DELETE FROM metadatas WHERE uuid = '" + META + "'");
        jdbc.executeQuery("DELETE FROM conditions WHERE uuid = 'qb-cond-min-len'");
        jdbc.executeQuery("DELETE FROM selectqueries WHERE uuid LIKE 'qb-%'");
        metadataService.invalidateMetadataCaches();
        metadataService.invalidateSecondaryCaches();
    }

    private Response get(String path) {
        return given().accept(ContentType.JSON).when().get(path);
    }

    private static List<String> values(Response r, String field) {
        List<Object> raw = r.then().statusCode(200).extract().jsonPath().getList(field);
        return raw.stream().map(String::valueOf).sorted().toList();
    }

    @Test
    void singleTriggerConditionFilters() {
        assertEquals(List.of("b3", "b4"), values(get("/api/" + TABLE + "?min_len=6&_limit=50"), "uuid"));
        // combined with a generic filter
        assertEquals(List.of("b4"), values(get("/api/" + TABLE + "?min_len=5&hull=surfski&_limit=50"), "uuid"));
    }

    @Test
    void sqlErrorsAreNotAnsweredAsEmptyLists() {
        // unknown column: a client error, no longer "200 []"
        get("/api/" + TABLE + "?no_such_column=1").then().statusCode(400);
    }

    @Test
    void groupedSelectQueryCanBeFilteredAndSorted() {
        Response all = get("/api/qb_by_hull?_sort=hull:asc");
        assertEquals(List.of("canoe", "kayak", "surfski"), all.then().statusCode(200).extract().jsonPath().getList("hull"));
        assertEquals("3", all.header("x-total-count"));
        Response kayak = get("/api/qb_by_hull?hull=kayak");
        assertEquals(List.of("2"), values(kayak, "boats"));
        assertEquals("1", kayak.header("x-total-count"));
    }

    @Test
    void selectQueryWithoutParamsIsPagedAndCounted() {
        Response page = get("/api/qb_all?_limit=2&_start=2");
        assertEquals(List.of("one", "three"), page.then().statusCode(200).extract().jsonPath().getList("name"));
        assertEquals("5", page.header("x-total-count"));
        // filters are still ignored without with_params
        assertEquals("5", get("/api/qb_all?hull=kayak").header("x-total-count"));
    }
}
