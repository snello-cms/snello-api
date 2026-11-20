package io.snello.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Keycloak groups as seen by Action scripts (binding {@code groups}): only the read the scripts need, not the whole
 * Keycloak admin of {@link AuthService}.
 * <pre>
 *   mail.send('Approve', html, groups.emails('Reviewers'), null, null, null)
 * </pre>
 */
@ApplicationScoped
public class GroupDirectory {

    @Inject
    AuthService authService;

    /** E-mails of the enabled members of the group (name, or path "Office/Reviewers"); unknown group = error. */
    public List<String> emails(String group) {
        Log.infof("script groups.emails('%s')", group);
        return authService.groupEmails(group);
    }
}
