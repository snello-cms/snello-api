package io.snello.service.multitenant;

import io.quarkus.logging.Log;
import io.quarkus.oidc.TenantResolver;
import io.quarkus.oidc.runtime.OidcUtils;
import io.snello.util.JwtUtils;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tools.jackson.databind.json.JsonMapper;

@ApplicationScoped
public class SnelloTenantResolver implements TenantResolver {

    @ConfigProperty(name = "quarkus.oidc.auth-server-url")
    String snello_auth;

    @ConfigProperty(name = "quarkus.oidc.snello.auth-server-url")
    String accounting_auth;

    @Inject
    JsonMapper objectMapper;

    @Override
    public String resolve(RoutingContext context) {
        String authorizationHeader = context.request().getHeader("Authorization");
        String issuer = JwtUtils.getIssuer(objectMapper, authorizationHeader);
        if (issuer != null && issuer.equals(accounting_auth)) {
            String tenant = "snello";
            Log.info("issuer: " + issuer + ", tenant resolved as: " + tenant);
            return tenant;
        }
        // Return the default tenant id explicitly, never null: on null Quarkus falls back to its path-based resolver,
        // which picks any path segment equal to a tenant id. With a tenant named "snello", a URL such as
        // /api/flow/definitions/snello/new-event/1.0.0 was then verified against the lovers realm -> 401.
        Log.info("issuer: " + issuer + ", tenant resolved as: " + OidcUtils.DEFAULT_TENANT_ID);
        return OidcUtils.DEFAULT_TENANT_ID;
    }


}
