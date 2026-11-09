package io.snello.filter;

import io.quarkus.logging.Log;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

@Provider
@PreMatching
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestCtx, ContainerResponseContext responseCtx) throws IOException {
        // No SecurityIdentity here: with lazy authentication it cannot be read on the event-loop thread (responses
        // such as a 404 are produced there) and threw BlockingOperationNotAllowedException on every such request.
        // The authenticated user is logged by the resources (ApiRequestUtils.logRequest).
        Log.debugf("%s - %s - status=%s - mediaType: %s", requestCtx.getMethod(), requestCtx.getUriInfo().getPath(),
                responseCtx.getStatus(), requestCtx.getMediaType());
        responseCtx.getHeaders().add("Access-Control-Allow-Origin", "*");
        responseCtx.getHeaders().add("Access-Control-Allow-Credentials", "true");
        responseCtx.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT, OPTIONS");
        responseCtx.getHeaders().add("Access-Control-Expose-Headers", "Authorization, authorization,Origin,origin,X-Requested-With, x-request-with,Content-Type,content-type,Accept,accept,X-total-count,x-total-count,size,Size");
        responseCtx.getHeaders().add("Access-Control-Max-Age", "1209600");
        responseCtx.getHeaders()
                .add("Access-Control-Allow-Headers",
                        "Authorization, authorization,Origin,origin,X-Requested-With, x-request-with,Content-Type,content-type,Accept,accept,X-total-count,x-total-count,size,Size, hostname, Pragma, mobile");
        try {
            MultivaluedMap<String, String> multiValuedMap = requestCtx.getHeaders();
            if (multiValuedMap.containsKey("Origin") && !responseCtx.getHeaders()
                    .containsKey("Access-Control-Allow-Origin")) {
                {
                    responseCtx.getHeaders().add("Access-Control-Allow-Origin", requestCtx.getHeaderString("Origin"));

                }
            }
        } catch (Exception e) {
            responseCtx.getHeaders().add("Access-Control-Allow-Origin", "*");
        }

    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (requestContext.getMethod().equals("OPTIONS")) {
            //            logger.info("OPTIONS METHOD DETECTED");
            Response.ResponseBuilder builder = Response.ok();
            requestContext.abortWith(builder.build());
        }
    }
}
