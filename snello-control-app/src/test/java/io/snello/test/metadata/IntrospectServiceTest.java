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
        jdbc.executeQuery("INSERT INTO " + CATEGORIES + " VALUES ('c1','Books','plain'), ('c2','Toys','also plain')");
        jdbc.executeQuery("INSERT INTO " + PRODUCTS
                + " VALUES ('p1','One','<p>Hello</p> world','c1','x'), ('p2','Two','<div>Hi</div>','c2','y')");
    }

    @AfterEach
    void cleanup() throws Exception {
        jdbc.executeQuery("DROP TABLE IF EXISTS " + BRIDGE);
        jdbc.executeQuery("DROP TABLE IF EXISTS " + PRODUCTS);
        jdbc.executeQuery("DROP TABLE IF EXISTS " + CATEGORIES);
        jdbc.executeQuery("DELETE FROM fielddefinitions WHERE metadata_name IN ('"
                + PRODUCTS + "','" + CATEGORIES + "')");
        jdbc.executeQuery("DELETE FROM metadatas WHERE table_name IN ('" + PRODUCTS + "','" + CATEGORIES + "')");
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

        assertTrue(byTable.get(BRIDGE).skipped(), "bridge should be skipped as entity");
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
        assertEquals("input", fields.get("mystery_id").get("type"), "unknown *_id stays plain");
        assertEquals("multijoin", fields.get(CATEGORIES).get("type"), "bridge → multijoin on products");

        Map<String, Map<String, Object>> catFields = new HashMap<>();
        byTable.get(CATEGORIES).fields().forEach(f -> catFields.put((String) f.get("name"), f));
        assertEquals("textarea", catFields.get("notes").get("type"), "wide varchar → textarea");
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
}
