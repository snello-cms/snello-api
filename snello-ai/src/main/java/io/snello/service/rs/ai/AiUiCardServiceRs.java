package io.snello.service.rs.ai;

import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.api.service.AbstractServiceRs;
import io.snello.management.SnelloPlane;
import io.snello.service.ApiService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import static io.snello.management.AppConstants.*;

@Path(AI_CARDS_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class AiUiCardServiceRs extends AbstractServiceRs {

    @Inject
    public AiUiCardServiceRs(ApiService apiService) {
        super(apiService, AI_UI_CARD, "name asc");
    }

    public AiUiCardServiceRs() {
        super();
    }

    @Override
    protected void prePersist(java.util.Map<String, Object> object) throws Exception {
        if (object.get(NAME) == null) {
            throw new Exception(MSG_NAME_PARAM_IS_EMPTY);
        }
    }
}
