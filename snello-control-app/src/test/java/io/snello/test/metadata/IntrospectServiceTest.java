package io.snello.test.metadata;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.snello.api.service.JdbcRepository;
import io.snello.model.pojo.IntrospectRequest;
import io.snello.model.pojo.IntrospectResult;
import io.snello.model.pojo.IntrospectedEntity;
import io.snello.service.IntrospectService;
import io.snello.service.MetadataService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class IntrospectServiceTest {

    private static final String CATEGORIES = "ix_categories";
    private static final String PRODUCTS = "ix_products";
    private static final String BRIDGE = "ix_products_ix_categories";
    /** integer SERIAL key: the URL key is a String, PostgreSQL refuses integer = varchar without a cast */
    private static final String SERIALS = "ix_serials";
    /** slug keys, a closed set of countries: key type and select are read from the data */
    private static final String MAKERS = "ix_makers";
    private static final String BOATS = "ix_boats";
    private static final String NATIVE = "ix_native";
    private static final List<String> EXTRA_TABLES = List.of(BOATS, MAKERS, NATIVE, "ix_pairs", "ix_nokey");
    private static final String COVER = "00000000-0000-4000-8000-0000000000c1";

    @Inject
    IntrospectService introspectService;

    @Inject
    JdbcRepository jdbc;

    @Inject
    MetadataService metadataService;

    @BeforeEach
    void seed() throws Exception {
        cleanup();
        jdbc.executeQuery("CREATE TABLE " + CATEGORIES
                + " (uuid varchar(50) PRIMARY KEY, name varchar(80), notes varchar(500))");
        jdbc.executeQuery("CREATE TABLE " + PRODUCTS
                + " (uuid varchar(50) PRIMARY KEY, title varchar(100), body text, "
                + "ix_categories_id varchar(50), mystery_id varchar(50))");
        jdbc.executeQuery("CREATE TABLE " + BRIDGE
                + " (ix_products_id varchar(50), ix_categories_id varchar(50))");
        jdbc.executeQuery("INSERT INTO " + CATEGORIES + " VALUES ('c1','Books','plain'), ('c2','Toys','"
                + "long ".repeat(40) + "')");
        jdbc.executeQuery("INSERT INTO " + PRODUCTS
                + " VALUES ('p1','One','<p>Hello</p> world','c1','x'), ('p2','Two','<div>Hi</div>','c2','y')");
    }

    @AfterEach
    void cleanup() throws Exception {
        jdbc.executeQuery("DROP TABLE IF EXISTS " + BRIDGE);
        jdbc.executeQuery("DROP TABLE IF EXISTS " + PRODUCTS);
        jdbc.executeQuery("DROP TABLE IF EXISTS " + CATEGORIES);
        jdbc.executeQuery("DROP TABLE IF EXISTS " + SERIALS);
        for (String table : EXTRA_TABLES) {
            jdbc.executeQuery("DROP TABLE IF EXISTS " + table);
            jdbc.executeQuery("DELETE FROM fielddefinitions WHERE metadata_name = '" + table + "'");
            jdbc.executeQuery("DELETE FROM metadatas WHERE table_name = '" + table + "'");
        }
        jdbc.executeQuery("DELETE FROM fielddefinitions WHERE metadata_name = '" + BRIDGE + "'");
        jdbc.executeQuery("DELETE FROM metadatas WHERE table_name = '" + BRIDGE + "'");
        jdbc.executeQuery("DELETE FROM documents WHERE uuid = '" + COVER + "'");
        jdbc.executeQuery("DELETE FROM fielddefinitions WHERE metadata_name IN ('"
                + PRODUCTS + "','" + CATEGORIES + "','" + SERIALS + "')");
        jdbc.executeQuery("DELETE FROM metadatas WHERE table_name IN ('" + PRODUCTS + "','" + CATEGORIES + "','"
                + SERIALS + "')");
        metadataService.invalidateMetadataCaches();
        metadataService.invalidateSecondaryCaches();
    }

    @Test
    void previewInfersHtmlTextareaJoinAndBridge() throws Exception {
        IntrospectResult r = introspectService.preview(new IntrospectRequest(
                List.of(CATEGORIES, PRODUCTS, BRIDGE), 20, "data"));
        assertTrue(r.valid(), String.join("\n", r.errors()));

        Map<String, IntrospectedEntity> byTable = new HashMap<>();
        r.entities().forEach(e -> byTable.put(e.table_name(), e));

        // a multijoin keeps its values in a column of the owner table: an existing bridge can only be a table
        assertFalse(byTable.get(BRIDGE).skipped(), "bridge is generated as a table with two joins");
        Map<String, Map<String, Object>> bridgeFields = new HashMap<>();
        byTable.get(BRIDGE).fields().forEach(f -> bridgeFields.put((String) f.get("name"), f));
        assertEquals(PRODUCTS, bridgeFields.get("ix_products_id").get("join_table_name"));
        assertEquals(CATEGORIES, bridgeFields.get("ix_categories_id").get("join_table_name"));
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains(BRIDGE) && w.contains("no primary key")));
        assertFalse(byTable.get(PRODUCTS).skipped());
        assertFalse(byTable.get(CATEGORIES).skipped());

        Map<String, Object> meta = byTable.get(PRODUCTS).metadata();
        assertEquals(true, meta.get("already_exist"));
        assertEquals("uuid", meta.get("table_key"));
        assertEquals("uuid", meta.get("table_key_type"));
        assertEquals("data", meta.get("metadata_group"));

        Map<String, Map<String, Object>> fields = new HashMap<>();
        byTable.get(PRODUCTS).fields().forEach(f -> fields.put((String) f.get("name"), f));

        assertFalse(fields.containsKey("uuid"), "generated uuid key is not a field");
        assertEquals("tinymce", fields.get("body").get("type"), "HTML samples → tinymce");
        assertEquals("join", fields.get("ix_categories_id").get("type"));
        assertEquals(CATEGORIES, fields.get("ix_categories_id").get("join_table_name"));
        assertEquals("name", fields.get("ix_categories_id").get("join_table_select_fields"),
                "join shows the target's name column (admin join widget needs select_fields)");
        assertEquals(false, fields.get("body").get("searchable"), "rich text stays out of the search form");
        assertEquals(true, fields.get("title").get("searchable"));
        assertEquals("input", fields.get("mystery_id").get("type"), "unknown *_id stays plain");
        assertFalse(fields.containsKey(CATEGORIES), "no multijoin field is invented on the owner");

        Map<String, Map<String, Object>> catFields = new HashMap<>();
        byTable.get(CATEGORIES).fields().forEach(f -> catFields.put((String) f.get("name"), f));
        assertEquals("textarea", catFields.get("notes").get("type"), "wide varchar with long values → textarea");
        assertEquals("name asc", byTable.get(CATEGORIES).metadata().get("order_by"), "label column orders the list");
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("mystery_id")));
    }

    @Test
    void applyPersistsAlreadyExistMetadata() throws Exception {
        IntrospectResult r = introspectService.apply(new IntrospectRequest(
                List.of(CATEGORIES, PRODUCTS), 10, null));
        assertTrue(r.valid(), String.join("\n", r.errors()));
        assertTrue(r.applied());
        assertNotNull(metadataService.metadataMap().get(PRODUCTS));
        assertEquals(true, metadataService.metadataMap().get(PRODUCTS).already_exist);
        assertFalse(metadataService.fielddefinitions(PRODUCTS).isEmpty());

        IntrospectResult again = introspectService.preview(new IntrospectRequest(
                List.of(PRODUCTS), 0, null));
        assertTrue(again.valid());
        assertTrue(again.entities().isEmpty(), "already-referenced tables are omitted from the result");
        assertTrue(again.warnings().stream().anyMatch(w -> w.contains(PRODUCTS) && w.contains("already referenced")));
    }

    @Test
    @TestSecurity(user = "admin", roles = {"Admin"})
    void previewEndpointReturnsEntities() {
        IntrospectResult r = given()
                .contentType(ContentType.JSON)
                .body(Map.of("tables", List.of(CATEGORIES), "sample_rows", 5))
                .when().post("/api/introspect/preview")
                .then().statusCode(200)
                .extract().as(IntrospectResult.class);
        assertTrue(r.valid());
        assertEquals(1, r.entities().size());
        assertEquals(CATEGORIES, r.entities().getFirst().table_name());
    }

    @Test
    @TestSecurity(user = "admin", roles = {"Admin"})
    void autoincrementTableIsFetchableAndDeletableByIdAfterApply() throws Exception {
        jdbc.executeQuery("CREATE TABLE " + SERIALS + " (id serial PRIMARY KEY, name varchar(80))");
        jdbc.executeQuery("INSERT INTO " + SERIALS + " (name) VALUES ('first'), ('second')");

        IntrospectResult r = introspectService.apply(new IntrospectRequest(List.of(SERIALS), 5, null));
        assertTrue(r.applied(), String.join("\n", r.errors()));
        assertEquals("autoincrement", r.entities().getFirst().metadata().get("table_key_type"));

        given().when().get("/api/" + SERIALS + "/1").then().statusCode(200)
                .body("name", org.hamcrest.Matchers.equalTo("first"));
        given().when().delete("/api/" + SERIALS + "/2").then().statusCode(org.hamcrest.Matchers.lessThan(300));
        assertEquals(1, jdbc.list("SELECT * FROM " + SERIALS).size(), "row 2 deleted by integer key");
    }

    /** What the schema does not say is read from the rows: key type, selects, references, uploaded files. */
    @Test
    void previewReadsKeyTypeSelectsAndReferencesFromData() throws Exception {
        jdbc.executeQuery("CREATE TABLE " + MAKERS + " (uuid varchar(50) PRIMARY KEY, name varchar(300) NOT NULL, "
                + "country varchar(2048), created_at timestamp NOT NULL DEFAULT now())");
        jdbc.executeQuery("INSERT INTO " + MAKERS + " (uuid, name, country) VALUES ('acme-boats','Acme Boats','Italy'),"
                + "('blue-wave','Blue Wave','Italy'),('north-sea','North Sea','Norway'),('red-rock','Red Rock','Norway'),"
                + "('sea-line','Sea Line','Italy'),('wind-co','Wind Co','Norway'),('wind-co-1','Wind Co','Italy')");
        jdbc.executeQuery("CREATE TABLE " + BOATS + " (id serial PRIMARY KEY, title text, ix_maker varchar(2048), "
                + "ix_category varchar(2048), origin varchar(2048), cover varchar(2048), length numeric(6,2), "
                + "active boolean, launched date)");
        jdbc.executeQuery("INSERT INTO documents (uuid, name, original_name, mimetype, table_name, table_key) "
                + "VALUES ('" + COVER + "','c.png','c.png','image/png','" + BOATS + "','1')");
        for (int i = 0; i < 6; i++) {
            jdbc.executeQuery("INSERT INTO " + BOATS + " (title, ix_maker, ix_category, origin, cover) VALUES ('Boat "
                    + i + "','" + (i % 2 == 0 ? "acme-boats" : "blue-wave") + "','unknown-" + i + "','"
                    + (i % 2 == 0 ? "c1" : "c2") + "','" + COVER + "')");
        }
        jdbc.executeQuery("CREATE TABLE ix_pairs (a integer, b integer, val varchar(50), PRIMARY KEY (a, b))");
        jdbc.executeQuery("CREATE TABLE ix_nokey (code varchar(20), note varchar(100))");

        IntrospectResult r = introspectService.preview(new IntrospectRequest(
                List.of(CATEGORIES, MAKERS, BOATS, "ix_pairs", "ix_nokey"), 30, null));
        assertTrue(r.valid(), String.join("\n", r.errors()));
        Map<String, IntrospectedEntity> byTable = new HashMap<>();
        r.entities().forEach(e -> byTable.put(e.table_name(), e));

        Map<String, Object> makers = byTable.get(MAKERS).metadata();
        assertEquals("slug", makers.get("table_key_type"), "keys are slugs of name, also with a -1 suffix");
        assertEquals("name", makers.get("table_key_addition"));
        assertEquals("name asc", makers.get("order_by"));
        Map<String, Map<String, Object>> makerFields = new HashMap<>();
        byTable.get(MAKERS).fields().forEach(f -> makerFields.put((String) f.get("name"), f));
        assertEquals("input", makerFields.get("name").get("type"), "wide varchar with short values stays an input");
        assertEquals(true, makerFields.get("name").get("mandatory"), "NOT NULL without default");
        assertEquals(false, makerFields.get("created_at").get("mandatory"), "NOT NULL with default is filled by the DB");
        assertEquals(false, makerFields.get("created_at").get("show_in_list"), "audit columns are not listed");
        assertEquals("select", makerFields.get("country").get("type"), "closed set of values → select");
        assertEquals("Italy,Norway", makerFields.get("country").get("options"));

        Map<String, Object> boats = byTable.get(BOATS).metadata();
        assertEquals("autoincrement", boats.get("table_key_type"));
        assertEquals("title asc", boats.get("order_by"));
        Map<String, Map<String, Object>> boatFields = new HashMap<>();
        byTable.get(BOATS).fields().forEach(f -> boatFields.put((String) f.get("name"), f));
        assertEquals("join", boatFields.get("ix_maker").get("type"), "column named after a table, values are its keys");
        assertEquals(MAKERS, boatFields.get("ix_maker").get("join_table_name"));
        assertEquals("name", boatFields.get("ix_maker").get("join_table_select_fields"));
        assertEquals("input", boatFields.get("ix_category").get("type"),
                "named after a table but the values are not its keys: not a join");
        assertEquals("join", boatFields.get("origin").get("type"), "every value is a key of one table → join");
        assertEquals(CATEGORIES, boatFields.get("origin").get("join_table_name"));
        assertEquals("media", boatFields.get("cover").get("type"), "values are documents.uuid → media");
        assertEquals(true, boatFields.get("title").get("searchable"), "label column is searchable");
        assertEquals(true, boatFields.get("title").get("show_in_list"), "a title declared as text is still listed");
        assertEquals(false, boatFields.get("length").get("searchable"), "numbers are not in the search form");
        assertEquals(false, boatFields.get("launched").get("show_in_list"));
        assertEquals(true, boatFields.get("active").get("show_in_list"), "flags are listed");

        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("ix_pairs") && w.contains("composite primary key")));
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("ix_nokey") && w.contains("no primary key")));
    }

    /** uuid / time / jsonb columns only exist in tables snello did not create: text from the form must still bind. */
    @Test
    @TestSecurity(user = "admin", roles = {"Admin"})
    void nativeColumnTypesAreReadableAndWritableAfterApply() throws Exception {
        jdbc.executeQuery("CREATE TABLE " + NATIVE + " (uuid uuid PRIMARY KEY, name varchar(80), at_time time, "
                + "payload jsonb)");
        jdbc.executeQuery("INSERT INTO " + NATIVE + " (uuid, name) VALUES ('" + COVER + "','first')");

        IntrospectResult r = introspectService.apply(new IntrospectRequest(List.of(NATIVE), 5, null));
        assertTrue(r.applied(), String.join("\n", r.errors()));
        assertEquals("uuid", r.entities().getFirst().metadata().get("table_key_type"));

        given().when().get("/api/" + NATIVE + "/" + COVER).then().statusCode(200)
                .body("name", org.hamcrest.Matchers.equalTo("first"));
        given().contentType(ContentType.JSON)
                .body(Map.of("name", "second", "at_time", "11:45", "payload", "{\"a\":2}"))
                .when().put("/api/" + NATIVE + "/" + COVER).then().statusCode(200);
        Map<String, Object> row = jdbc.list("SELECT name, at_time, payload->>'a' AS a FROM " + NATIVE).getFirst();
        assertEquals("second", row.get("name"));
        assertEquals("11:45:00", String.valueOf(row.get("at_time")));
        assertEquals("2", row.get("a"));
    }
}
