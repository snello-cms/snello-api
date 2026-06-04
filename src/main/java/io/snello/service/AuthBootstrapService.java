package io.snello.service;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class AuthBootstrapService {

    @Inject
    AuthService authService;

    public void onLoad(@Observes StartupEvent event) {
        authService.bootstrapBaseSecurity();
        Log.info("Keycloak bootstrap completed: groups Admin/Manager/User verified and admin user ensured");
    }
}
