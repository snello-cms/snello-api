package io.snello.service.rs;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.api.service.MailService;
import io.snello.service.AuthService;
import io.snello.management.SnelloPlane;
import io.snello.model.pojo.MailSendRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static io.snello.management.AppConstants.MAIL_PATH;

/**
 * {@code POST /api/mail/send}: sends a mail through snello's SMTP configuration ({@code SNELLO_MAIL_*}).
 * Lets other services (e.g. the Quarkus Flow workflow {@code snello:new-event}) send mail without holding mail
 * credentials of their own: one place for the provider (Brevo, Resend, ...). Admin/Manager only, like config writes,
 * so a service account calling it needs the Manager realm role. Recipient count is capped
 * ({@code snello.mail.send.max-recipients}) so a leaked token cannot turn snello into a bulk mailer.
 */
@Path(MAIL_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class MailServiceRs {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    @Inject
    MailService mailService;

    @Inject
    SecurityIdentity identity;

    @Inject
    AuthService authService;

    @ConfigProperty(name = "snello.mail.send.max-recipients", defaultValue = "20")
    int maxRecipients;

    @POST
    @Path("/send")
    @RolesAllowed({"Admin", "admin", "Manager", "manager"})
    public Response send(MailSendRequest request) {
        String caller = identity == null || identity.getPrincipal() == null ? null : identity.getPrincipal().getName();
        if (request == null) {
            throw new BadRequestException("body is required");
        }
        List<String> to = clean("to", request.to);
        List<String> cc = clean("cc", request.cc);
        List<String> bcc = clean("bcc", request.bcc);
        addGroups("toGroups", request.toGroups, to);
        addGroups("ccGroups", request.ccGroups, cc);
        addGroups("bccGroups", request.bccGroups, bcc);
        Log.infof("MAIL api send requested by %s: subject='%s' to=%d cc=%d bcc=%d htmlLength=%d", caller,
                request.subject, to.size(), cc.size(), bcc.size(), request.html == null ? 0 : request.html.length());
        if (to.isEmpty()) {
            throw new BadRequestException("to / toGroups: at least one recipient is required"
                    + (request.toGroups == null || request.toGroups.isEmpty() ? "" : " (the groups have no member with an e-mail)"));
        }
        if (request.subject == null || request.subject.isBlank()) {
            throw new BadRequestException("subject is required");
        }
        int total = to.size() + cc.size() + bcc.size();
        if (total > maxRecipients) {
            Log.warnf("MAIL api send refused for %s: %d recipients > max %d", caller, total, maxRecipients);
            throw new BadRequestException("too many recipients: " + total + " > " + maxRecipients);
        }
        String html = request.html == null ? "" : request.html;
        try {
            mailService.send(request.subject, html, to, cc.isEmpty() ? null : cc, bcc.isEmpty() ? null : bcc, null);
        } catch (RuntimeException e) {
            // SMTP failures (auth, rejected sender, timeout): 502 so the caller can tell them from its own errors
            // and retry; the workflow retries 5xx.
            Log.errorf(e, "MAIL api send failed for %s: subject='%s'", caller, request.subject);
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity(Map.of("error", "mail not sent: " + e.getMessage())).build();
        }
        Log.infof("MAIL api send done for %s: subject='%s' recipients=%d", caller, request.subject, total);
        return Response.ok(Map.of("sent", true, "recipients", total)).build();
    }

    /** Adds the e-mails of each Keycloak group's members to {@code into} (no duplicates). */
    private void addGroups(String field, List<String> groups, List<String> into) {
        if (groups == null) {
            return;
        }
        for (String g : groups) {
            if (g == null || g.isBlank()) {
                continue;
            }
            List<String> emails = authService.groupEmails(g);
            int before = into.size();
            for (String e : emails) {
                if (!into.contains(e)) {
                    into.add(e);
                }
            }
            Log.infof("MAIL api %s '%s' -> %d addresses (%d new)", field, g.trim(), emails.size(), into.size() - before);
        }
    }

    private static List<String> clean(String field, List<String> addresses) {
        List<String> out = new ArrayList<>();
        if (addresses == null) {
            return out;
        }
        for (String a : addresses) {
            if (a == null || a.isBlank()) {
                continue;
            }
            String trimmed = a.trim();
            if (!EMAIL.matcher(trimmed).matches()) {
                throw new BadRequestException(field + ": invalid address '" + trimmed + "'");
            }
            out.add(trimmed);
        }
        return out;
    }
}
