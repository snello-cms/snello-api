package io.snello.service;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.snello.management.SnelloPlane;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Keeps a query node's metadata cache in sync with the control plane.
 * <p>
 * Query nodes never receive the control plane's CDI events, so they poll: every
 * {@value SnelloPlane#METADATA_REFRESH_INTERVAL_PROPERTY} the system tables are reloaded and the snapshot is
 * swapped only when its checksum moved. Set the property to {@code off} (control default) to disable.
 */
@ApplicationScoped
public class MetadataRefreshJob {

    @Inject
    MetadataService metadataService;

    @Scheduled(identity = "snello-metadata-refresh",
            every = "${" + SnelloPlane.METADATA_REFRESH_INTERVAL_PROPERTY + ":off}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void refresh() {
        try {
            boolean changed = metadataService.refreshIfChanged();
            if (changed) {
                Log.infof("metadata refresh: new snapshot published version=%s", metadataService.version());
            }
        } catch (Exception e) {
            // keep serving the previous snapshot: a transient DB error must not empty the cache
            Log.errorf(e, "metadata refresh failed, keeping snapshot version=%s", metadataService.version());
        }
    }
}
