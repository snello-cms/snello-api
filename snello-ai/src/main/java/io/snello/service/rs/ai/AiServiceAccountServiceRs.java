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
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import static io.snello.management.AppConstants.*;

@Path(AI_SERVICE_ACCOUNTS_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class AiServiceAccountServiceRs extends AbstractServiceRs {

    @Inject
    public AiServiceAccountServiceRs(ApiService apiService) {
        super(apiService, AI_SERVICE_ACCOUNT, "name asc");
    }

    public AiServiceAccountServiceRs() {
        super();
    }

    @Override
    protected void prePersist(Map<String, Object> object) throws Exception {
        if (object.get(NAME) == null) {
            throw new Exception(MSG_NAME_PARAM_IS_EMPTY);
        }
    }

    @Override
    protected void postFetch(Map<String, Object> object) {
        redact(object);
    }

    @Override
    public Response list(
            String sort,
            String limit,
            String start,
            jakarta.ws.rs.core.UriInfo uriInfo) throws Exception {
        Response response = super.list(sort, limit, start, uriInfo);
        if (response.getEntity() instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> maps = (List<Map<String, Object>>) list;
            maps.forEach(this::redact);
        }
        return response;
    }

    @Override
    public Response fetch(String id) throws Exception {
        Response response = super.fetch(id);
        if (response.getEntity() instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) map;
            redact(row);
        }
        return response;
    }

    private void redact(Map<String, Object> row) {
        if (row == null) {
            return;
        }
        row.remove("client_secret");
    }
}
