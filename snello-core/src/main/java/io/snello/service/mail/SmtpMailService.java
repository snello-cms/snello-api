package io.snello.service.mail;

import io.quarkus.logging.Log;
import io.quarkus.mailer.Attachment;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.snello.api.service.MailService;

import java.util.List;

public class SmtpMailService implements MailService {

    Mailer mailer;

    public SmtpMailService(Mailer mailer) {
        this.mailer = mailer;
    }

    @Override
    public void send(String subject, String body, List<String> to, List<String> cc, List<String> bcc, List<Attachment> attachments) {
        if (to == null || to.isEmpty()) {
            throw new IllegalArgumentException("mail has no recipients (to is empty)");
        }
        Log.infof("MAIL send subject='%s' to=%s cc=%s bcc=%s attachments=%d", subject, to, cc, bcc,
                attachments == null ? 0 : attachments.size());
        // Mail.withHtml(to, ...) already sets the first recipient; setTo(...) would REPLACE it, so use the
        // full list (the old code passed subList(1..) and sent mails with no To at all).
        Mail mail = new Mail().setSubject(subject).setHtml(body).setTo(to);
        if (cc != null && !cc.isEmpty()) {
            mail.setCc(cc);
        }
        if (bcc != null && !bcc.isEmpty()) {
            mail.setBcc(bcc);
        }
        if (attachments != null && !attachments.isEmpty()) {
            mail.setAttachments(attachments);
        }
        mailer.send(mail);
        Log.infof("MAIL handed to mailer, subject='%s' recipients=%d", subject, to.size());
    }
}
