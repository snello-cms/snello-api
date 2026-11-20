package io.snello.service.producer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The dialect follows the driver's db-kind, Quarkus aliases included. */
class DbProducerDialectTest {

    @Test
    void dialectFollowsDbKind() {
        assertEquals("postgresql", DbProducer.dialectOf("postgresql"));
        assertEquals("postgresql", DbProducer.dialectOf("pgsql"));
        assertEquals("mysql", DbProducer.dialectOf("MySQL"));
        assertEquals("mysql", DbProducer.dialectOf("mariadb"));
        assertEquals("mssql", DbProducer.dialectOf("mssql"));
        assertEquals("mssql", DbProducer.dialectOf("sqlserver"));
        assertEquals("h2", DbProducer.dialectOf(" h2 "));
        assertEquals("oracle", DbProducer.dialectOf("oracle"));   // unknown: DbProducer then fails with a clear message
    }
}
