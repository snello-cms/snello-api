package io.snello.test.metadata;

import io.quarkus.test.junit.QuarkusTest;
import io.snello.model.enums.InputType;
import io.snello.model.enums.SearchOp;
import io.snello.model.enums.SemanticType;
import io.snello.model.enums.WidgetType;
import io.snello.service.EntityCatalog;
import io.snello.service.repository.SqlQueryBuilder;
import io.snello.util.ColumnSpecs;
import io.snello.util.FieldDefinitionUtils;
import io.snello.util.FieldUiDerivation;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class EnumAndCatalogTest {

    @Inject
    EntityCatalog catalog;

    @Test
    void enumsRoundTripKnownLiterals() {
        assertEquals(WidgetType.JOIN, WidgetType.from("join"));
        assertTrue(WidgetType.MULTIJOIN.isMultiJoin());
        assertEquals(InputType.EMAIL, InputType.from("email"));
        assertEquals(SearchOp.CONTAINS, SearchOp.from("_contains"));
        assertEquals(SemanticType.REFERENCE, SemanticType.from("reference"));
    }

    @Test
    void fieldDefinitionUtilsUsesEnums() {
        assertTrue(FieldDefinitionUtils.isMultiJoinType("multiselect"));
        assertFalse(FieldDefinitionUtils.isMultiJoinType("input"));
    }

    @Test
    void columnSpecsAndUiDerivationUseEnums() {
        assertNotNull(ColumnSpecs.kindForWidget("input", "number"));
        String[] ui = FieldUiDerivation.uiTypeFor("email");
        assertEquals("input", ui[0]);
        assertEquals("email", ui[1]);
    }

    @Test
    void slugKeyVarcharGrowsToSourceField() {
        var uuidQuery = new io.snello.model.facet.EntityQueryFacet("uuid", "uuid", null,
                null, null, null, null, null, false, null);
        assertEquals(ColumnSpecs.DEFAULT_KEY_VARCHAR,
                ColumnSpecs.resolveKeyVarcharLength(uuidQuery, java.util.List.of()));

        var slugQuery = new io.snello.model.facet.EntityQueryFacet("uuid", "slug", "title",
                null, null, null, null, null, false, null);
        var title = new io.snello.model.pojo.ColumnSpec("title",
                io.snello.model.pojo.ColumnKind.SHORT_TEXT, null, false, null, null, "input");
        assertEquals(200, ColumnSpecs.resolveKeyVarcharLength(slugQuery, java.util.List.of(title)));

        var longTitle = new io.snello.model.pojo.ColumnSpec("title",
                io.snello.model.pojo.ColumnKind.LONG_TEXT, null, false, null, null, "textarea");
        assertEquals(ColumnSpecs.LONG_TEXT_KEY_VARCHAR,
                ColumnSpecs.resolveKeyVarcharLength(slugQuery, java.util.List.of(longTitle)));

        var custom = new io.snello.model.pojo.ColumnSpec("title",
                io.snello.model.pojo.ColumnKind.SHORT_TEXT, "title VARCHAR(500) default null",
                false, null, null, "sql_definition");
        assertEquals(500, ColumnSpecs.resolveKeyVarcharLength(slugQuery, java.util.List.of(custom)));
    }

    @Test
    void sqlQueryBuilderEscapesPerDialect() {
        assertEquals("`orders`", SqlQueryBuilder.MYSQL.escape("orders"));
        assertEquals("orders", SqlQueryBuilder.PLAIN.escape("orders"));
        String insert = SqlQueryBuilder.MYSQL.create("kayaks", java.util.Map.of("uuid", "1", "name", "x"));
        assertTrue(insert.contains("`kayaks`"));
        assertTrue(insert.contains("`uuid`") || insert.contains("`name`"));
    }

    @Test
    void catalogExposesUiAndAiViews() throws Exception {
        assertFalse(catalog.queryViews().isEmpty());
        String name = catalog.queryViews().keySet().iterator().next();
        assertTrue(catalog.uiView(name).isPresent());
        assertTrue(catalog.aiView(name).isPresent());
        assertFalse(catalog.fieldModels(name).isEmpty());
    }
}
