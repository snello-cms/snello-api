package io.snello.service.rs.ai;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.management.SnelloPlane;
import io.snello.model.ai.AiProject;
import io.snello.service.ai.skills.ProjectAccess;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

import static io.snello.management.AppConstants.AI_ME_PROJECTS_PATH;

@Path(AI_ME_PROJECTS_PATH)
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class MyProjectsRs {

    @Inject
    ProjectAccess access;

    @Inject
    SecurityIdentity identity;

    @GET
    public List<Map<String, Object>> mine() throws Exception {
        return access.mine(identity).stream().map(AiProject::toMap).toList();
    }

    @GET
    @Path("/{uuid}")
    public Response byUuid(@PathParam("uuid") String uuid) throws Exception {
        return access.findByUuid(uuid)
                .map(project -> {
                    if (!access.canAccess(identity, project)) {
                        return Response.status(Response.Status.FORBIDDEN).build();
                    }
                    return Response.ok(project.toMap()).build();
                })
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }
}
