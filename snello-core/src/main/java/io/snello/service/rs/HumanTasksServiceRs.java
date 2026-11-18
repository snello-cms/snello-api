package io.snello.service.rs;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.management.SnelloPlane;
import io.snello.model.pojo.HumanTaskCloseRequest;
import io.snello.model.pojo.HumanTaskDecisionRequest;
import io.snello.service.HumanTaskService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.snello.management.AppConstants.HUMAN_TASKS_PATH;

/**
 * Human tasks (decisions workflows wait for), see {@link HumanTaskService}:
 * <pre>
 * POST /api/humantasks                     create (Admin / Manager: the workflow's service account)
 *      {title, description?, page_name?, workflow_namespace?, workflow_name?, workflow_version?, instance_id?,
 *       record_table?, record_key?, decisions? ("APPROVE,REJECT"), roles? ("Admin,Manager"), data?}
 *      -> the task with its page url: {public url}/p/{page_name | task}?task={uuid}
 * GET  /api/humantasks?status=&instance_id=&record_table=&record_key=&workflow_name=&limit=
 * GET  /api/humantasks/{uuid}
 * GET  /api/humantasks/{uuid}/context      task + record + fields + workflow position + viewer (what pages show)
 * POST /api/humantasks/{uuid}/decide       {decision, comment?}   viewer with one of the task's roles, task OPEN
 * POST /api/humantasks/{uuid}/undo         clears the decision while the task is OPEN
 * POST /api/humantasks/{uuid}/close        {note?}  the workflow applied it (Admin / Manager)
 * POST /api/humantasks/{uuid}/cancel       {note?}  (Admin / Manager)
 * </pre>
 * Not anonymous at all (tasks point at personal data). Viewers: Admin / Manager, or a role listed in the task.
 */
@Path(HUMAN_TASKS_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class HumanTasksServiceRs {

    @Inject
    HumanTaskService service;

    @Inject
    SecurityIdentity identity;

    @POST
    @RolesAllowed({"Admin", "admin", "Manager", "manager"})
    public Map<String, Object> create(Map<String, Object> body) throws Exception {
        return service.create(body == null ? Map.of() : body, identity);
    }

    @GET
    @Authenticated
    public List<Map<String, Object>> list(@QueryParam("status") String status, @QueryParam("instance_id") String instanceId,
                                          @QueryParam("record_table") String recordTable,
                                          @QueryParam("record_key") String recordKey,
                                          @QueryParam("workflow_name") String workflowName,
                                          @QueryParam("workflow_namespace") String workflowNamespace,
                                          @QueryParam("limit") @DefaultValue("100") int limit) throws Exception {
        Map<String, String> f = new HashMap<>();
        f.put("status", status);
        f.put("instance_id", instanceId);
        f.put("record_table", recordTable);
        f.put("record_key", recordKey);
        f.put("workflow_name", workflowName);
        f.put("workflow_namespace", workflowNamespace);
        return service.list(f, limit, identity);
    }

    @GET
    @Path("/{uuid}")
    @Authenticated
    public Map<String, Object> get(@PathParam("uuid") String uuid) throws Exception {
        return service.get(uuid, identity);
    }

    @GET
    @Path("/{uuid}/context")
    @Authenticated
    public Map<String, Object> context(@PathParam("uuid") String uuid) throws Exception {
        return service.context(uuid, identity);
    }

    @POST
    @Path("/{uuid}/decide")
    @Authenticated
    public Map<String, Object> decide(@PathParam("uuid") String uuid, HumanTaskDecisionRequest body) throws Exception {
        return service.decide(uuid, body == null ? null : body.decision(), body == null ? null : body.comment(), identity);
    }

    @POST
    @Path("/{uuid}/undo")
    @Authenticated
    public Map<String, Object> undo(@PathParam("uuid") String uuid) throws Exception {
        return service.undo(uuid, identity);
    }

    @POST
    @Path("/{uuid}/close")
    @RolesAllowed({"Admin", "admin", "Manager", "manager"})
    public Map<String, Object> close(@PathParam("uuid") String uuid, HumanTaskCloseRequest body) throws Exception {
        return service.close(uuid, HumanTaskService.CLOSED, body == null ? null : body.note(), identity);
    }

    @POST
    @Path("/{uuid}/cancel")
    @RolesAllowed({"Admin", "admin", "Manager", "manager"})
    public Map<String, Object> cancel(@PathParam("uuid") String uuid, HumanTaskCloseRequest body) throws Exception {
        return service.close(uuid, HumanTaskService.CANCELLED, body == null ? null : body.note(), identity);
    }
}
