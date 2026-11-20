package io.snello.model.pojo;

import java.util.List;

/**
 * Body of {@code POST /api/mail/send}: a mail sent through snello's own SMTP configuration
 * ({@code SNELLO_MAIL_*}), so callers such as Quarkus Flow workflows need no mail credentials.
 */
public class MailSendRequest {
    /** Recipients; at least one address here or in {@link #toGroups}. */
    public List<String> to;
    public List<String> cc;
    public List<String> bcc;
    /**
     * Keycloak groups whose members get the mail ("Reviewers", or "Office/Reviewers" for a subgroup): snello reads
     * the members' e-mails, so the caller does not need to know who is in the group.
     */
    public List<String> toGroups;
    public List<String> ccGroups;
    /** Group members in bcc: they do not see each other's address. */
    public List<String> bccGroups;
    public String subject;
    /** HTML body. */
    public String html;
}
