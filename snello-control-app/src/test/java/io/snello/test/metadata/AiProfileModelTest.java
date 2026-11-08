package io.snello.test.metadata;

import io.snello.model.FieldDefinition;
import io.snello.model.Metadata;
import io.snello.model.entity.EntityModel;
import io.snello.model.entity.FieldModel;
import io.snello.model.facet.EntityAiFacet;
import io.snello.model.facet.EntityIdentity;
import io.snello.model.facet.FieldAiFacet;
import io.snello.model.pojo.EntityAiProfile;
import io.snello.model.pojo.FieldAiProfile;
import io.snello.util.AiProfileUtils;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiProfileModelTest {

    @Test
    void entityFacetReadsProfileColumnAndIgnoresUnknownKeys() {
        Metadata m = new Metadata(Map.of(
                "table_name", "clients",
                "description", "Customers of the shop",
                "ai_profile", "{\"summary\":\"who buys\",\"synonyms\":[\"customers\"],\"tool_visible\":false,"
                        + "\"examples\":[{\"name\":\"ACME\"}],\"future_key\":42}"));
        EntityAiFacet ai = m.aiFacet();
        assertEquals("Customers of the shop", ai.description());
        assertEquals("who buys", ai.summary());
        assertEquals(List.of("customers"), ai.synonyms());
        assertFalse(ai.tool_visible());
        assertEquals("ACME", ai.examples().getFirst().get("name"));
    }

    @Test
    void missingOrCorruptProfileMeansVisibleAndEmpty() {
        assertTrue(new Metadata(Map.of("table_name", "a")).aiFacet().tool_visible());
        EntityAiFacet corrupt = new Metadata(Map.of("table_name", "a", "ai_profile", "{not json")).aiFacet();
        assertTrue(corrupt.tool_visible());
        assertNull(corrupt.summary());
    }

    @Test
    void defaultsSerializeToNullSoTheColumnStaysNull() {
        assertNull(EntityAiFacet.ofDescription("only a description").toProfileJson());
        assertNull(FieldAiFacet.ofDescription("only a description").toProfileJson());
    }

    @Test
    void entityModelRoundTripsThroughTheFlatRow() {
        EntityModel model = new EntityModel();
        EntityIdentity.Builder identity = EntityIdentity.builder(model.identity);
        identity.table_name = "clients";
        model.identity = identity.build();
        EntityAiFacet.Builder ai = EntityAiFacet.builder(model.ai);
        ai.description = "Customers";
        ai.synonyms = List.of("customers", "buyers");
        ai.tool_visible = false;
        model.ai = ai.build();
        Map<String, Object> row = model.toMap();
        assertFalse(row.containsKey("synonyms"), "AI attributes must live inside ai_profile, not as columns");
        EntityModel back = new EntityModel(row);
        assertEquals(List.of("customers", "buyers"), back.ai.synonyms());
        assertFalse(back.ai.tool_visible());
        assertEquals(row.get("ai_profile"), back.toMetadata().ai_profile);
    }

    @Test
    void fieldModelStoresAiAttributesInsideProfileOnly() {
        FieldModel model = new FieldModel();
        model.name = "country";
        FieldAiFacet.Builder ai = FieldAiFacet.builder(model.ai);
        ai.ai_hint = "ISO-3166 alpha-2, uppercase";
        ai.semantic_type = "country_code";
        ai.examples = List.of("IT", "DE");
        model.ai = ai.build();
        Map<String, Object> row = model.toMap();
        assertFalse(row.containsKey("ai_hint"));
        assertFalse(row.containsKey("semantic_type"));
        assertFalse(row.containsKey("examples"));
        FieldAiFacet back = new FieldDefinition(row).aiFacet();
        assertEquals("country_code", back.semantic_type());
        assertEquals(List.of("IT", "DE"), back.examples());
    }

    @Test
    void normalizeAcceptsObjectOrStringAndRejectsInvalid() {
        Map<String, Object> asObject = new HashMap<>(Map.of("ai_profile", Map.of("synonyms", List.of("x"))));
        AiProfileUtils.normalizeColumn(asObject, EntityAiProfile.class);
        assertInstanceOf(String.class, asObject.get("ai_profile"));
        assertEquals("{\"synonyms\":[\"x\"]}", asObject.get("ai_profile"),
                "canonical JSON: no nulls, no helper properties like isEmpty()");
        assertEquals(List.of("x"), AiProfileUtils.parseEntity((String) asObject.get("ai_profile")).synonyms());

        Map<String, Object> blank = new HashMap<>(Map.of("ai_profile", " "));
        AiProfileUtils.normalizeColumn(blank, FieldAiProfile.class);
        assertNull(blank.get("ai_profile"));

        Map<String, Object> invalid = new HashMap<>(Map.of("ai_profile", "{broken"));
        assertThrows(IllegalArgumentException.class, () -> AiProfileUtils.normalizeColumn(invalid, EntityAiProfile.class));

        Map<String, Object> wrongShape = new HashMap<>(Map.of("ai_profile", "{\"synonyms\":\"not-a-list\"}"));
        assertThrows(IllegalArgumentException.class, () -> AiProfileUtils.normalizeColumn(wrongShape, EntityAiProfile.class));
    }
}
