package io.snello.test.metadata;

import io.snello.model.FieldDefinition;
import io.snello.model.Metadata;
import io.snello.util.SlugKeySizes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlugKeySizesTest {

    @Test
    void uuidKeyStaysDefault() {
        Metadata m = new Metadata();
        m.table_key_type = "uuid";
        assertEquals(SlugKeySizes.DEFAULT_KEY_VARCHAR, resolve(m, List.of()));
    }

    @Test
    void slugKeyGrowsToShortTextSource() {
        Metadata m = new Metadata();
        m.table_key_type = "slug";
        m.table_key_addition = "title";
        FieldDefinition title = new FieldDefinition();
        title.name = "title";
        title.type = "input";
        title.input_type = "text";
        assertEquals(200, resolve(m, List.of(title)));
    }

    @Test
    void slugKeyGrowsToMediumTextSource() {
        Metadata m = new Metadata();
        m.table_key_type = "slug";
        m.table_key_addition = "name";
        FieldDefinition name = new FieldDefinition();
        name.name = "name";
        name.type = "select";
        assertEquals(2048, resolve(m, List.of(name)));
    }

    @Test
    void slugKeyHonorsSqlDefinitionLength() {
        Metadata m = new Metadata();
        m.table_key_type = "slug";
        m.table_key_addition = "title";
        FieldDefinition title = new FieldDefinition();
        title.name = "title";
        title.type = "input";
        title.input_type = "text";
        title.sql_definition = "title VARCHAR(500) default null";
        assertEquals(500, resolve(m, List.of(title)));
    }

    private static int resolve(Metadata m, List<FieldDefinition> fields) {
        return SlugKeySizes.resolveKeyVarcharLength(m, fields);
    }
}
