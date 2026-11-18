package io.snello.service.rs;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.api.service.AbstractServiceRs;
import io.snello.management.SnelloPlane;
import io.snello.service.ApiService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static io.snello.management.AppConstants.WORKFLOW_PAGES;
import static io.snello.management.AppConstants.WORKFLOW_PAGES_PATH;

/**
 * CRUD for {@code workflowpages} (writes: Admin / Manager, see AdminConfigWriteFilter). A page is HTML + JS served at
 * {@code /p/{name}?task={uuid}} by {@link PublicPagesRs}, with the {@code snello} page client (login, REST, task
 * context, decide / undo). Pages hold no data: everything personal is fetched by the page with the viewer's token.
 */
@Path(WORKFLOW_PAGES_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class WorkflowPagesServiceRs extends AbstractServiceRs {

    /** Part of the URL /p/{name}: lower case letters, digits and '-'. */
    static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9-]{0,99}");
    /** Served by snello itself: /p/task (generic page), /p/_/... (page client). */
    static final Set<String> RESERVED = Set.of("task", "_");

    @Inject
    WorkflowPagesServiceRs(ApiService apiService) {
        super(apiService, WORKFLOW_PAGES, "name asc");
    }

    public WorkflowPagesServiceRs() {
    }

    @Override
    protected void prePersist(Map<String, Object> map) throws Exception {
        validate(map);
        requireUniqueName(map.get("name").toString(), null);
        super.prePersist(map);
    }

    @Override
    protected void preUpdate(String id, Map<String, Object> map) throws Exception {
        validate(map);
        requireUniqueName(map.get("name").toString(), id);
        super.preUpdate(id, map);
    }

    /** The name is the page's URL: two rows with one name would make /p/{name} serve either of them. */
    private void requireUniqueName(String name, String selfId) throws Exception {
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add("name", name);
        List<Map<String, Object>> same = apiService.list(WORKFLOW_PAGES, params, "name asc", 10, 0);
        boolean taken = same.stream().anyMatch(r -> name.equals(r.get("name")) && !String.valueOf(r.get("uuid")).equals(selfId));
        if (taken) {
            Log.infof("workflow page name '%s' already used -> 409", name);
            throw new WebApplicationException(Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("msg", "a workflow page named '" + name + "' already exists")).build());
        }
    }

    private static void validate(Map<String, Object> map) {
        Object name = map.get("name");
        if (name == null || !NAME.matcher(name.toString()).matches()) {
            Log.warnf("workflow page: invalid name '%s'", name);
            throw new BadRequestException("name: lower case letters, digits and '-' (it is the URL /p/{name})");
        }
        if (RESERVED.contains(name.toString())) {
            throw new BadRequestException("name '" + name + "' is reserved");
        }
        Log.infof("workflow page %s: workflow=%s:%s roles=%s body=%s chars", name, map.get("workflow_namespace"),
                map.get("workflow_name"), map.get("roles"),
                map.get("body") == null ? 0 : map.get("body").toString().length());
    }
}
