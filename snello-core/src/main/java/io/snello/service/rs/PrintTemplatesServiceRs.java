package io.snello.service.rs;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.api.service.AbstractServiceRs;
import io.snello.management.SnelloPlane;
import io.snello.model.PrintTemplate;
import io.snello.model.pojo.PrintOutput;
import io.snello.service.ApiService;
import io.snello.service.PrintTemplateService;
import io.snello.util.MapReads;
import io.snello.util.SqlSafety;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static io.snello.management.AppConstants.PRINT_TEMPLATES;
import static io.snello.management.AppConstants.PRINT_TEMPLATES_PATH;
import static io.snello.util.ApiRequestUtils.rowFilter;

/**
 * CRUD for {@code printtemplates} (writes: Admin/Manager) plus the print itself:
 * <pre>
 * GET /api/printtemplates/{name}/print?city=Rome&price_gte=10&_sort=name:asc
 *     _inline=true    show in the browser instead of downloading
 *     _separator=;    separator of the automatic CSV (default ,)
 * </pre>
 * Every other parameter filters the query result by column alias (same operators as the data API: _ne, _gt,
 * _contains, _in, ...). A protected template answers 401 to anonymous callers and shows non-admin callers only
 * their rows.
 */
@Path(PRINT_TEMPLATES_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class PrintTemplatesServiceRs extends AbstractServiceRs {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]+");

    @Inject
    PrintTemplateService printTemplateService;

    @Context
    SecurityContext securityContext;

    @Context
    UriInfo uriInfo;

    @Inject
    PrintTemplatesServiceRs(ApiService apiService) {
        super(apiService, PRINT_TEMPLATES, "name asc");
    }

    public PrintTemplatesServiceRs() {
    }

    @Override
    protected void prePersist(Map<String, Object> object) throws Exception {
        validate(object);
        if (printTemplateService.findByName(MapReads.str(object, "name")) != null) {
            throw new BadRequestException("a print template named " + object.get("name") + " already exists");
        }
    }

    @Override
    protected void preUpdate(String id, Map<String, Object> object) throws Exception {
        Map<String, Object> stored = printTemplateService.findRow(id);
        if (stored == null) {
            throw new NotFoundException("print template " + id + " not found");
        }
        // PUT may be partial: validate the row as it will be stored
        Map<String, Object> merged = new HashMap<>(stored);
        merged.putAll(object);
        validate(merged);
        if (object.containsKey("export_type")) {
            object.put("export_type", merged.get("export_type"));
        }
        PrintTemplate sameName = printTemplateService.findByName(MapReads.str(merged, "name"));
        if (sameName != null && !id.equals(sameName.uuid)) {
            throw new BadRequestException("a print template named " + merged.get("name") + " already exists");
        }
    }

    private void validate(Map<String, Object> object) {
        PrintTemplate t = new PrintTemplate(object);
        String type = t.export_type == null ? null : t.export_type.trim().toLowerCase();
        Log.infof("printtemplate validate name=%s type=%s api_protected=%s username_field=%s template_file=%s",
                t.name, type, t.api_protected, t.username_field, t.template_file_name);
        if (t.name == null || !NAME.matcher(t.name).matches()) {
            throw new BadRequestException("name is required and may only contain letters, digits, _ and - (it is part of the print URL)");
        }
        if (type == null || !PrintTemplate.EXPORT_TYPES.contains(type)) {
            throw new BadRequestException("export_type must be one of " + PrintTemplate.EXPORT_TYPES);
        }
        object.put("export_type", type);
        if (t.sql_query == null || t.sql_query.isBlank()) {
            throw new BadRequestException("sql_query is required");
        }
        if (t.api_protected && (t.username_field == null || t.username_field.isBlank())) {
            throw new BadRequestException("api_protected print template requires username_field");
        }
        if (t.username_field != null && !t.username_field.isBlank() && SqlSafety.isUnsafeOwnerColumn(t.username_field)) {
            throw new BadRequestException("username_field must be a plain column name");
        }
        if ((PrintTemplate.PDF.equals(type) || PrintTemplate.HTML.equals(type))
            && (t.html_body == null || t.html_body.isBlank())) {
            throw new BadRequestException(type + " export needs html_body (a Qute template)");
        }
        if (PrintTemplate.XLSX.equals(type)) {
            if (t.template_file == null || t.template_file.isBlank()) {
                throw new BadRequestException("xlsx export needs template_file (a JXLS .xlsx template)");
            }
            try {
                PrintTemplateService.decodeTemplateFile(t.template_file);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(e.getMessage());
            }
        }
    }

    @GET
    @Path("/{name}/print")
    @Produces(MediaType.WILDCARD)
    public Response print(@PathParam("name") String name) throws Exception {
        MultivaluedMap<String, String> query = uriInfo.getQueryParameters();
        Log.infof("print %s requested with %s", name, query);
        PrintTemplate template = printTemplateService.findByName(name);
        if (template == null) {
            Log.infof("print %s: no such template -> 404", name);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        // filters = every parameter not starting with "_" (those steer the print itself)
        MultivaluedMap<String, String> filters = new MultivaluedHashMap<>();
        query.forEach((k, v) -> {
            if (!k.startsWith("_")) {
                filters.put(k, List.copyOf(v));
            }
        });
        Response denied = rowFilter(template.rowProtection(), "printtemplate " + name, filters, securityContext);
        if (denied != null) {
            return denied;
        }
        PrintOutput out;
        try {
            out = printTemplateService.print(template, filters, query.getFirst("_sort"), query.getFirst("_separator"));
        } catch (IllegalArgumentException e) {
            Log.infof("print %s rejected: %s", name, e.getMessage());
            return error(Response.Status.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            // template / SQL errors: the admin needs the message to fix the template
            Log.errorf(e, "print %s failed", name);
            return error(Response.Status.BAD_REQUEST, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        boolean inline = Boolean.parseBoolean(query.getFirst("_inline"));
        return Response.ok(out.bytes(), out.contentType())
                .header("Content-Disposition", (inline ? "inline" : "attachment") + "; filename=\"" + out.fileName() + "\"")
                .header("X-Snello-Rows", out.rows())
                .header("Access-Control-Expose-Headers", "Content-Disposition, X-Snello-Rows")
                .build();
    }

    private static Response error(Response.Status status, String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", message == null ? "error" : message)).build();
    }
}
