package io.snello.service.rs.ai;

import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.api.service.AbstractServiceRs;
import io.snello.management.SnelloPlane;
import io.snello.service.ApiService;
import io.snello.service.ai.AiRowDefaults;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import static io.snello.management.AppConstants.AI_PROJECT;
import static io.snello.management.AppConstants.AI_PROJECTS_PATH;
import static io.snello.management.AppConstants.NAME;
import static io.snello.management.AppConstants.MSG_NAME_PARAM_IS_EMPTY;

@Path(AI_PROJECTS_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class AiProjectServiceRs extends AbstractServiceRs {

    @Inject
    public AiProjectServiceRs(ApiService apiService) {
        super(apiService, AI_PROJECT, "name asc");
    }

    public AiProjectServiceRs() {
        super();
    }

    @Override
    protected void prePersist(java.util.Map<String, Object> object) throws Exception {
        if (object.get(NAME) == null) {
            throw new Exception(MSG_NAME_PARAM_IS_EMPTY);
        }
        AiRowDefaults.blankOnInsert(AI_PROJECT, object, "global_message", "roles", "sso_group");
    }

    @Override
    protected void preUpdate(java.util.Map<String, Object> object) throws Exception {
        if (object.get(NAME) == null) {
            throw new Exception(MSG_NAME_PARAM_IS_EMPTY);
        }
        AiRowDefaults.blankOnUpdate(AI_PROJECT, object, "global_message", "roles", "sso_group");
    }
}
