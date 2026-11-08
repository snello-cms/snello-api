package io.snello.service;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.snello.api.service.JdbcRepository;
import io.snello.management.SnelloPlane;
import io.snello.model.events.DbCreatedEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Creates / upgrades the system tables at startup. Control plane only: query nodes never run DDL.
 * <p>
 * Runs with a low {@link Priority} so the schema exists before {@link EventService} preloads the caches
 * (previously both were un-ordered {@code StartupEvent} observers).
 */
@ApplicationScoped
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class SchemaBootstrapService {

    @Inject
    JdbcRepository jdbcRepository;

    @Inject
    Event<DbCreatedEvent> dbCreatedEvent;

    void onStart(@Observes @Priority(100) StartupEvent event) {
        Log.infof("schema bootstrap: running system-table creation/upgrade queries repository=%s",
                jdbcRepository.getClass().getSimpleName());
        jdbcRepository.onLoad();
        jdbcRepository.applyUpgrades();
        dbCreatedEvent.fireAsync(new DbCreatedEvent());
        Log.info("schema bootstrap: done");
    }
}
