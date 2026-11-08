package io.snello.service.rs;

import io.quarkus.smallrye.openapi.runtime.OpenApiConstants;
import io.quarkus.smallrye.openapi.runtime.OpenApiDocumentService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.openapi.runtime.io.Format;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;

import static io.snello.management.AppConstants.OPENAPI_PATH;

/**
 * Serves the generated OpenAPI document as JSON at {@code GET /openapi}
 * (all discovered JAX-RS endpoints of the running plane).
 */
@Path(OPENAPI_PATH)
@ApplicationScoped
@RunOnVirtualThread
public class OpenApiServiceRs {

    @Inject
    OpenApiDocumentService openApiDocumentService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(hidden = true)
    public Response openApiJson() {
        byte[] json = openApiDocumentService.getDocument(OpenApiConstants.DEFAULT_DOCUMENT_NAME, Format.JSON);
        if (json == null || json.length == 0) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("{\"error\":\"openapi document not available\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        return Response.ok(json).type(MediaType.APPLICATION_JSON_TYPE).build();
    }
}
