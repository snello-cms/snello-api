package io.snello.service.rs;

import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.service.DataOpenApiService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static io.snello.management.AppConstants.OPENAPI_PATH;

/**
 * OpenAPI document of the data API, generated from the metadata (public, like {@code /openapi}):
 * <ul>
 *   <li>{@code GET /openapi/data/{table}}: one table (the layouts "API" button);</li>
 *   <li>{@code GET /openapi/data?group=Acquisti}: the tables of one metadata_group;</li>
 *   <li>{@code GET /openapi/data?tables=a,b}: a chosen set; no parameter = every table;</li>
 *   <li>{@code GET /openapi/data/tables}: the table names (to pick from).</li>
 * </ul>
 */
@Path(OPENAPI_PATH + "/data")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
public class DataOpenApiServiceRs {

    @Inject
    DataOpenApiService service;

    @GET
    @Operation(hidden = true)
    public Response all(@QueryParam("group") String group, @QueryParam("tables") String tables) throws Exception {
        List<String> names = tables == null || tables.isBlank() ? List.of()
                : Arrays.stream(tables.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        Log.infof("GET /openapi/data group=%s tables=%s", group, names);
        Map<String, Object> doc = service.document(names, group);
        if (doc == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "no table matches group=" + group + " tables=" + names)).build();
        }
        return Response.ok(doc).build();
    }

    @GET
    @Path("/tables")
    @Operation(hidden = true)
    public List<String> tables() throws Exception {
        return service.tables();
    }

    @GET
    @Path("/{table}")
    @Operation(hidden = true)
    public Response table(@PathParam("table") String table) throws Exception {
        Log.infof("GET /openapi/data/%s", table);
        Map<String, Object> doc = service.document(List.of(table), null);
        if (doc == null) {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "unknown table " + table)).build();
        }
        return Response.ok(doc).build();
    }
}
