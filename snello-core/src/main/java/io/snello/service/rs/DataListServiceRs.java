package io.snello.service.rs;

import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.management.SnelloPlane;
import io.snello.model.FieldDefinition;
import io.snello.model.Metadata;
import io.snello.model.entity.EntityModel;
import io.snello.model.entity.FieldModel;
import io.snello.model.pojo.FieldQueryView;
import io.snello.model.pojo.FieldUiView;
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

import java.util.Comparator;
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
        Metadata metadata = metadataService.metadata(name);
        if (metadata == null) {
            return ok((Object) null).build();
        }
        EntityModel model = EntityModel.from(metadata);
        model.fields = null;
        model.conditions = null;
        if (model.query != null) {
            model.query.table_key = null;
        }
        return ok(model).build();
    }

    /**
     * Full field models for a metadata: identity + nested {@code query}/{@code ui}/{@code ai} facets.
     * Prefer the dedicated {@code /fields/ui} and {@code /fields/query} endpoints when the caller needs one concern.
     */
    @GET
    @Path("/metadata/{name}/fielddefinitions")
    public Response fielddefinitions(@PathParam("name") @NotNull String name) throws Exception {
        return ok(fieldModels(name)).build();
    }

    /** Query-plane field projection only (SQL / join / search). */
    @GET
    @Path("/metadata/{name}/fields/query")
    public Response fieldsQuery(@PathParam("name") @NotNull String name) throws Exception {
        List<FieldQueryView> views = fields(name).stream().map(FieldQueryView::of).toList();
        return ok(views).build();
    }

    /** UI-plane field projection only (widget / form / list). */
    @GET
    @Path("/metadata/{name}/fields/ui")
    public Response fieldsUi(@PathParam("name") @NotNull String name) throws Exception {
        List<FieldUiView> views = fields(name).stream().map(FieldUiView::of).toList();
        return ok(views).build();
    }

    @GET
    @Path("/metadata/{name}/condition")
    public Response conditions(@PathParam("name") @NotNull String name) throws Exception {
        return ok(metadataService.conditions(name)).build();
    }

    private List<FieldDefinition> fields(String name) throws Exception {
        List<FieldDefinition> list = metadataService.fielddefinitions(name);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return list.stream()
                .sorted(Comparator
                        .comparingInt((FieldDefinition fd) -> fd.view_index)
                        .thenComparing(fd -> fd.name == null ? "" : fd.name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<FieldModel> fieldModels(String name) throws Exception {
        return fields(name).stream().map(FieldModel::from).toList();
    }
}
