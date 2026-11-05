package io.snello.service.mail;

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
        mailer.send(
                Mail.withHtml(to.get(0), subject, body)
                        .setTo(to.subList(1, to.size()))
                        .setBcc(bcc)
                        .setCc(cc)
                        .setAttachments(attachments)
        );
    }
}
