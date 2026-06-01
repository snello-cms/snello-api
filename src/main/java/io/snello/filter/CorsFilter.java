package io.snello.filter;

import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

@Provider
@PreMatching
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Inject
    SecurityIdentity securityIdentity;

    @Override
    public void filter(ContainerRequestContext requestCtx, ContainerResponseContext responseCtx) throws IOException {
        try {
            Log.info(requestCtx.getMethod() + " - " + requestCtx.getUriInfo().getPath()
                    + " - mediaType: " + requestCtx.getMediaType()
                    + ", (" + securityIdentity.getPrincipal().getName() + ")"
                    + ", [" + securityIdentity.getRoles() + "]"
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
        responseCtx.getHeaders().add("Access-Control-Allow-Origin", "true");
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
