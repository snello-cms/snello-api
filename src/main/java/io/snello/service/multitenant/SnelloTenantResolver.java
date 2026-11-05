package io.snello.service.multitenant;

import io.quarkus.logging.Log;
import io.quarkus.oidc.TenantResolver;
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
        if (issuer.equals(accounting_auth)) {
            String tenant = "snello";
            Log.info("issuer: " + issuer + ", tenant resolved as: " + tenant);
            return tenant;
        }
        String tenant = "default";
        Log.info("issuer: " + issuer + ", tenant resolved as: " + tenant);
        return tenant;
    }


}
