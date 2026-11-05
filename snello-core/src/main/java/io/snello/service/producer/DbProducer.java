package io.snello.service.producer;

import io.agroal.api.AgroalDataSource;
import io.quarkus.logging.Log;
import io.snello.api.service.JdbcRepository;
import io.snello.service.repository.h2.H2JdbcRepository;
import io.snello.service.repository.mysql.MysqlJdbcRepository;
import io.snello.service.repository.postgresql.PostgresqlJdbcRepository;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Selects the JDBC dialect implementation for the single default Agroal datasource.
 * <p>
 * Database <em>driver</em> selection is a Quarkus build-time concern ({@code quarkus.datasource.db-kind}),
 * specialized via mutable-jar re-augmentation (Keycloak-style). {@code snello.dbtype} must match that
 * build-time kind so the correct {@link JdbcRepository} dialect is used.
 */
@Singleton
public class DbProducer {

    @ConfigProperty(name = "snello.dbtype", defaultValue = "postgresql")
    String dbtype;

    @Inject
    AgroalDataSource dataSource;

    public DbProducer() {
        Log.info("DbProducer");
    }

    @Produces
    public JdbcRepository db() throws Exception {
        Log.info("dbtype: " + dbtype);
        return switch (dbtype) {
            case "mysql" -> new MysqlJdbcRepository(dataSource);
            case "postgresql" -> new PostgresqlJdbcRepository(dataSource);
            case "h2" -> new H2JdbcRepository(dataSource);
            default -> throw new Exception("no dbtype: " + dbtype + " (expected mysql|postgresql|h2)");
        };
    }
}
