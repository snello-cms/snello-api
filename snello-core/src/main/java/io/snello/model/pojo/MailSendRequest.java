package io.snello.model.pojo;

import java.util.List;

/**
 * Body of {@code POST /api/mail/send}: a mail sent through snello's own SMTP configuration
 * ({@code SNELLO_MAIL_*}), so callers such as Quarkus Flow workflows need no mail credentials.
 */
public class MailSendRequest {
    /** Recipients; at least one. */
    public List<String> to;
    public List<String> cc;
    public List<String> bcc;
    public String subject;
    /** HTML body. */
    public String html;
}
