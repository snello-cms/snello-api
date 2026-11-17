package io.snello.test.repository;

import io.snello.model.enums.KeyType;
import io.snello.model.facet.EntityQueryFacet;
import io.snello.model.pojo.ColumnKind;
import io.snello.model.pojo.ColumnSpec;
import io.snello.service.repository.SqlQueryBuilder;
import io.snello.service.repository.mssql.MssqlJdbcRepository;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MssqlJdbcRepositorySmokeTest {

    private static final DataSource NO_DB = (DataSource) Proxy.newProxyInstance(
            MssqlJdbcRepositorySmokeTest.class.getClassLoader(), new Class[]{DataSource.class}, (p, m, a) -> null);

    @Test
    void bracketsEscapeIdentifiers() {
        assertEquals("[year]", SqlQueryBuilder.MSSQL.escape("year"));
        assertEquals("[a]]b]", SqlQueryBuilder.MSSQL.escape("a]b"));
    }

    @Test
    void columnAndKeySqlUseNvarcharBitIdentity() {
        MssqlJdbcRepository repo = new MssqlJdbcRepository(NO_DB);
        assertTrue(repo.columnSql(new ColumnSpec("title", ColumnKind.SHORT_TEXT, null, false, null, null, null))
                .toLowerCase().contains("nvarchar"));
        assertTrue(repo.columnSql(new ColumnSpec("active", ColumnKind.BOOLEAN, null, false, null, null, null))
                .toLowerCase().contains("bit"));

        EntityQueryFacet uuidKey = new EntityQueryFacet("uuid", KeyType.UUID.dbValue(), null, null, null, null, null,
                null, false, null);
        assertTrue(repo.keyColumnSql(uuidKey, 50).toLowerCase().contains("nvarchar"));

        EntityQueryFacet autoKey = new EntityQueryFacet("id", KeyType.AUTOINCREMENT.dbValue(), null, null, null, null,
                null, null, false, null);
        assertTrue(repo.keyColumnSql(autoKey, 50).toLowerCase().contains("identity"));

        EntityQueryFacet userKey = new EntityQueryFacet("code", KeyType.USERDEFINED.dbValue(), null, null, null, null,
                null, null, false, null);
        assertNull(repo.keyColumnSql(userKey, 50));

        assertTrue(repo.addColumnSql("boats", "[len] int").contains(" ADD "));
        assertTrue(repo.renameColumnSql("boats", "len", "length").contains("sp_rename"));
        assertTrue(repo.renameTableSql("boats", "kayaks").contains("sp_rename"));
    }
}
