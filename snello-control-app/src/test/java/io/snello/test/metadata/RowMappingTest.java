package io.snello.test.metadata;

import io.snello.model.FieldDefinition;
import io.snello.model.entity.FieldModel;
import io.snello.model.pojo.ColumnKind;
import io.snello.util.ColumnSpecs;
import io.snello.util.FieldUiDerivation;
import io.snello.util.MetadataUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RowMappingTest {

    @Test
    void fieldRowsCarryOnlyRealColumnsAndKeepRequiredAsMandatory() {
        // LinksServiceRs inserts fieldDefinition.toMap() as-is: a "required" key made the INSERT fail
        FieldDefinition fd = new FieldDefinition();
        fd.name = "x";
        fd.required = true;
        Map<String, Object> row = fd.toMap();
        assertFalse(row.containsKey("required"));
        assertEquals(true, row.get("mandatory"));

        FieldModel model = new FieldModel();
        model.name = "x";
        model.ui.required = true;
        assertFalse(model.toMap().containsKey("required"));
        assertEquals(true, model.toMap().get("mandatory"));
    }

    @Test
    void everySystemPathIsReserved() {
        for (String name : new String[]{"metadatas", "fielddefinitions", "conditions", "actions", "auth", "backup",
                "datalist", "documents", "drafts", "links", "p", "selectqueries", "ai_tools", "history_session", "history_event", "ai_project", "ai_skill", "ai_mcp_tool", "ai_ui_card", "ai_service_account", "ai", "agui"}) {
            assertTrue(MetadataUtils.isReserved(name), name);
        }
        assertFalse(MetadataUtils.isReserved("products"));
        assertFalse(MetadataUtils.isReserved(null));
    }

    @Test
    void semanticTypesMapToDdlCapableWidgets() {
        for (String semantic : FieldUiDerivation.SEMANTIC_TYPES) {
            assertNotNull(FieldUiDerivation.uiTypeFor(semantic)[0], semantic);
        }
        assertEquals("decimal", FieldUiDerivation.semanticFromSqlType("numeric(10,2)"));
        assertEquals("integer", FieldUiDerivation.semanticFromSqlType("NUMERIC(10)"));
        assertEquals("datetime", FieldUiDerivation.semanticFromSqlType("timestamptz"));
        assertNull(FieldUiDerivation.semanticFromSqlType("geometry"));
        assertThrows(IllegalArgumentException.class, () -> FieldUiDerivation.uiTypeFor("colour"));
    }

    @Test
    void columnKindComesFromWidgetThenSqlType() {
        FieldDefinition fd = new FieldDefinition();
        fd.name = "price";
        fd.sql_type = "numeric(10,2)";
        assertEquals(ColumnKind.DECIMAL, ColumnSpecs.of(fd).kind(), "no widget: query.sql_type decides");
        fd.type = "input";
        fd.input_type = "number";
        assertEquals(ColumnKind.INTEGER, ColumnSpecs.of(fd).kind(), "widget wins over sql_type");
        fd.type = "geometry";
        fd.sql_type = null;
        assertNull(ColumnSpecs.of(fd).kind());
        assertTrue(ColumnSpecs.of(fd).sourceType().contains("geometry"));
        fd.type = "multijoin";
        assertTrue(ColumnSpecs.of(fd).multiJoin());
    }
}
