package io.snello.service.producer;

import io.agroal.api.AgroalDataSource;
import io.quarkus.logging.Log;
import io.snello.api.service.JdbcRepository;
import io.snello.service.repository.h2.H2JdbcRepository;
import io.snello.service.repository.mssql.MssqlJdbcRepository;
import io.snello.service.repository.mysql.MysqlJdbcRepository;
import io.snello.service.repository.postgresql.PostgresqlJdbcRepository;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Selects the JDBC dialect implementation for the single default Agroal datasource.
 * <p>
 * The dialect follows the driver: {@code quarkus.datasource.db-kind}, fixed at build time or by mutable-jar
 * re-augmentation ({@code SNELLO_DB_KIND} + snello-optimize.sh). The legacy {@code snello.dbtype} property is unused
 * and ignored if still present in an environment.
 */
@ApplicationScoped
public class DbProducer {

    @ConfigProperty(name = "quarkus.datasource.db-kind", defaultValue = "postgresql")
    String dbKind;

    @Inject
    AgroalDataSource dataSource;

    public DbProducer() {
        Log.info("DbProducer");
    }

    @Produces
    @ApplicationScoped
    public JdbcRepository db() throws Exception {
        String kind = dialectOf(dbKind);
        Log.infof("dialect: %s (quarkus.datasource.db-kind=%s)", kind, dbKind);
        return switch (kind) {
            case "mysql" -> new MysqlJdbcRepository(dataSource);
            case "postgresql" -> new PostgresqlJdbcRepository(dataSource);
            case "h2" -> new H2JdbcRepository(dataSource);
            case "mssql" -> new MssqlJdbcRepository(dataSource);
            default -> throw new Exception("no dialect for db-kind: " + dbKind + " (expected mysql|postgresql|h2|mssql)");
        };
    }

    /** Quarkus db-kind aliases (pgsql, mariadb, sqlserver, ...) to the snello dialect name. */
    static String dialectOf(String dbKind) {
        String k = dbKind == null ? "" : dbKind.trim().toLowerCase();
        return switch (k) {
            case "postgresql", "postgres", "pgsql", "pg" -> "postgresql";
            case "mysql", "mariadb" -> "mysql";
            case "mssql", "sqlserver", "microsoft-sql-server" -> "mssql";
            default -> k;
        };
    }
}
