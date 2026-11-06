package io.snello.test.metadata;

import io.snello.api.service.JdbcRepository;
import io.snello.model.FieldDefinition;
import io.snello.model.pojo.ColumnKind;
import io.snello.service.repository.h2.H2JdbcRepository;
import io.snello.service.repository.mysql.MysqlJdbcRepository;
import io.snello.service.repository.postgresql.PostgresqlJdbcRepository;
import io.snello.util.ColumnSpecs;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class FieldDefinitionUtilsTypeCoverageTest {

    private static final DataSource NO_DB = (DataSource) Proxy.newProxyInstance(
            FieldDefinitionUtilsTypeCoverageTest.class.getClassLoader(), new Class[]{DataSource.class}, (p, m, a) -> null);

    @ParameterizedTest
    @ValueSource(strings = {"relationships", "realtionships"})
    void relationshipTypesAreVarcharOnEveryDialect(String type) throws Exception {
        FieldDefinition fieldDefinition = new FieldDefinition();
        fieldDefinition.name = "relazioni";
        fieldDefinition.type = type;
        assertEquals(ColumnKind.MEDIUM_TEXT, ColumnSpecs.of(fieldDefinition).kind());
        for (JdbcRepository repo : new JdbcRepository[]{new H2JdbcRepository(NO_DB), new MysqlJdbcRepository(NO_DB),
                new PostgresqlJdbcRepository(NO_DB)}) {
            String sql = repo.fieldDefinition2Sql(fieldDefinition);
            assertNotNull(sql, repo.getClass().getSimpleName());
            assertTrue(sql.toLowerCase().contains("varchar"), sql);
        }
    }
}
