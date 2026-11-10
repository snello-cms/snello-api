package io.snello.service.rs;

import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.management.SnelloPlane;
import io.snello.model.FieldDefinition;
import io.snello.model.Metadata;
import io.snello.model.entity.EntityModel;
import io.snello.model.facet.EntityQueryFacet;
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
     * Legacy SPA contract: flat {@link Metadata} (same shape as Snello 5 / kayak.love).
     * {@code table_key} is omitted; fields/conditions are not embedded — use the field endpoints.
     * For 6.x facet composition see {@code /metadata/{name}/model}.
     */
    @GET
    @Path("/metadata/{name}")
    public Response metadata(@PathParam("name") @NotNull String name) throws Exception {
        Metadata src = metadataService.metadata(name);
        if (src == null) {
            return ok((Metadata) null).build();
        }
        // Copy so we do not mutate the in-memory catalog entry.
        Metadata view = new Metadata(src.toMap());
        view.table_key = null;
        view.fields = null;
        view.conditions = null;
        return ok(view).build();
    }

    /**
     * 6.x facet composition ({@code identity}/{@code query}/{@code ui}/{@code ai}) for AI / new clients.
     */
    @GET
    @Path("/metadata/{name}/model")
    public Response metadataModel(@PathParam("name") @NotNull String name) throws Exception {
        return catalog.entity(name)
                .map(model -> {
                    model.fields = null;
                    model.conditions = null;
                    if (model.query != null) {
                        EntityQueryFacet.Builder query = EntityQueryFacet.builder(model.query);
                        query.table_key = null;
                        model.query = query.build();
                    }
                    return ok(model).build();
                })
                .orElseGet(() -> ok((EntityModel) null).build());
    }

    /**
     * Legacy SPA contract: flat {@link FieldDefinition} rows (same shape as Snello 5).
     * For nested facet models see {@code /metadata/{name}/fieldmodels}.
     */
    @GET
    @Path("/metadata/{name}/fielddefinitions")
    public Response fielddefinitions(@PathParam("name") @NotNull String name) throws Exception {
        List<FieldDefinition> fields = metadataService.fielddefinitions(name);
        return ok(fields).build();
    }

    /** Full field models with nested {@code query}/{@code ui}/{@code ai} facets. */
    @GET
    @Path("/metadata/{name}/fieldmodels")
    public Response fieldModels(@PathParam("name") @NotNull String name) throws Exception {
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
