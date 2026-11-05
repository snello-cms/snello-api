package io.snello.test.metadata;

import io.snello.model.FieldDefinition;
import io.snello.service.repository.h2.H2FieldDefinitionUtils;
import io.snello.service.repository.mysql.MysqlFieldDefinitionUtils;
import io.snello.service.repository.postgresql.PostgresqlFieldDefinitionUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldDefinitionUtilsTypeCoverageTest {

    @Test
    void shouldMapRelationshipsTypeForAllRepositories() throws Exception {
        FieldDefinition fieldDefinition = new FieldDefinition();
        fieldDefinition.name = "relazioni";
        fieldDefinition.type = "relationships";

        String h2Sql = H2FieldDefinitionUtils.sql(fieldDefinition);
        String mysqlSql = MysqlFieldDefinitionUtils.sql(fieldDefinition);
        String postgresqlSql = PostgresqlFieldDefinitionUtils.sql(fieldDefinition);

        assertNotNull(h2Sql);
        assertNotNull(mysqlSql);
        assertNotNull(postgresqlSql);

        assertTrue(h2Sql.toLowerCase().contains("varchar"));
        assertTrue(mysqlSql.toLowerCase().contains("varchar"));
        assertTrue(postgresqlSql.toLowerCase().contains("varchar"));
    }

    @Test
    void shouldSupportCommonMisspellingRealtionships() throws Exception {
        FieldDefinition fieldDefinition = new FieldDefinition();
        fieldDefinition.name = "relazioni";
        fieldDefinition.type = "realtionships";

        String h2Sql = H2FieldDefinitionUtils.sql(fieldDefinition);
        String mysqlSql = MysqlFieldDefinitionUtils.sql(fieldDefinition);
        String postgresqlSql = PostgresqlFieldDefinitionUtils.sql(fieldDefinition);

        assertNotNull(h2Sql);
        assertNotNull(mysqlSql);
        assertNotNull(postgresqlSql);

        assertTrue(h2Sql.toLowerCase().contains("varchar"));
        assertTrue(mysqlSql.toLowerCase().contains("varchar"));
        assertTrue(postgresqlSql.toLowerCase().contains("varchar"));
    }
}
