package io.snello.service.rs;

import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.api.service.AbstractServiceRs;
import io.snello.management.SnelloPlane;
import io.snello.service.ApiService;
import io.snello.service.LayoutService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.HashMap;
import java.util.Map;

import static io.snello.management.AppConstants.LAYOUTS;
import static io.snello.management.AppConstants.LAYOUTS_PATH;

/**
 * CRUD for SPA {@code layouts} (writes: Admin/Manager via {@code AdminConfigWriteFilter}).
 * Columns hold identity / filters; {@code json_data} is the page body only.
 */
@Path(LAYOUTS_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class LayoutsServiceRs extends AbstractServiceRs {

    @Inject
    LayoutService layoutService;

    @Inject
    LayoutsServiceRs(ApiService apiService) {
        super(apiService, LAYOUTS, "updated_at desc");
    }

    public LayoutsServiceRs() {
    }

    @Override
    protected void prePersist(Map<String, Object> object) throws Exception {
        layoutService.prepareWrite(object);
    }

    @Override
    protected void preUpdate(String id, Map<String, Object> object) throws Exception {
        Map<String, Object> stored = layoutService.findRow(id);
        if (stored == null) {
            throw new NotFoundException("layout " + id + " not found");
        }
        Map<String, Object> merged = new HashMap<>(stored);
        merged.putAll(object);
        layoutService.prepareWrite(merged);
        // PUT may be partial: write the validated merged values back into the update map
        object.putAll(merged);
        object.remove("uuid");
    }
}
