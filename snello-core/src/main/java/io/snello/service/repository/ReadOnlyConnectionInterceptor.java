package io.snello.service.repository;

import io.agroal.api.AgroalPoolInterceptor;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import io.snello.management.SnelloPlane;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Query-plane only: mark every acquired JDBC connection read-only so accidental writes fail at the driver/DB.
 */
@ApplicationScoped
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "false")
public class ReadOnlyConnectionInterceptor implements AgroalPoolInterceptor {

    @Override
    public void onConnectionAcquire(Connection connection) {
        try {
            connection.setReadOnly(true);
        } catch (SQLException e) {
            Log.warnf(e, "query-plane: could not set connection read-only");
            throw new IllegalStateException("query-plane requires read-only JDBC connections", e);
        }
    }
}
