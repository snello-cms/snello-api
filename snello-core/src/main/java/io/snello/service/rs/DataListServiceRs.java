package io.snello.service.rs;

import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.management.SnelloPlane;
import io.snello.model.entity.EntityModel;
import io.snello.model.pojo.EntityAiView;
import io.snello.model.pojo.EntityQueryView;
import io.snello.model.pojo.EntityUiView;
import io.snello.model.pojo.FieldQueryView;
import io.snello.model.pojo.FieldUiView;
import io.snello.service.EntityCatalog;
import io.snello.service.MetadataService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

import static io.snello.management.AppConstants.DATALIST_PATH;
import static jakarta.ws.rs.core.Response.ok;

@Path(DATALIST_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class DataListServiceRs {

    @Inject
    MetadataService metadataService;

    @Inject
    EntityCatalog catalog;

    @GET
    @Path("/names")
    public Response names() throws Exception {
        return ok(metadataService.names()).build();
    }

    /**
     * Metadata for the SPA as facet composition ({@code identity}/{@code query}/{@code ui}/{@code ai}).
     * {@code query.table_key} is omitted (legacy SPA contract). Fields are not embedded — use the field endpoints.
     */
    @GET
    @Path("/metadata/{name}")
    public Response metadata(@PathParam("name") @NotNull String name) throws Exception {
        return catalog.entity(name)
                .map(model -> {
                    model.fields = null;
                    model.conditions = null;
                    if (model.query != null) {
                        model.query.table_key = null;
                    }
                    return ok(model).build();
                })
                .orElseGet(() -> ok((EntityModel) null).build());
    }

    /**
     * Full field models for a metadata: identity + nested {@code query}/{@code ui}/{@code ai} facets.
     */
    @GET
    @Path("/metadata/{name}/fielddefinitions")
    public Response fielddefinitions(@PathParam("name") @NotNull String name) throws Exception {
        return ok(catalog.fieldModels(name)).build();
    }

    /** Query-plane field projection only (SQL / join / search). */
    @GET
    @Path("/metadata/{name}/fields/query")
    public Response fieldsQuery(@PathParam("name") @NotNull String name) throws Exception {
        List<FieldQueryView> views = catalog.queryView(name)
                .map(EntityQueryView::fields)
                .orElse(List.of());
        return ok(views).build();
    }

    /** UI-plane field projection only (widget / form / list). */
    @GET
    @Path("/metadata/{name}/fields/ui")
    public Response fieldsUi(@PathParam("name") @NotNull String name) throws Exception {
        List<FieldUiView> views = catalog.uiView(name)
                .map(EntityUiView::fields)
                .orElse(List.of());
        return ok(views).build();
    }

    /** AI-plane field projection only. */
    @GET
    @Path("/metadata/{name}/fields/ai")
    public Response fieldsAi(@PathParam("name") @NotNull String name) throws Exception {
        return ok(catalog.aiView(name).map(EntityAiView::fields).orElse(List.of())).build();
    }

    /** Full UI view (entity + fields). */
    @GET
    @Path("/metadata/{name}/ui")
    public Response uiView(@PathParam("name") @NotNull String name) throws Exception {
        return ok(catalog.uiView(name).orElse(null)).build();
    }

    /** Full AI view (entity + fields). */
    @GET
    @Path("/metadata/{name}/ai")
    public Response aiView(@PathParam("name") @NotNull String name) throws Exception {
        return ok(catalog.aiView(name).orElse(null)).build();
    }

    @GET
    @Path("/metadata/{name}/condition")
    public Response conditions(@PathParam("name") @NotNull String name) throws Exception {
        return ok(metadataService.conditions(name)).build();
    }
}
