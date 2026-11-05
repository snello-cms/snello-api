package io.snello.service.producer;

import io.quarkus.logging.Log;
import io.quarkus.mailer.Mailer;
import io.snello.api.service.MailService;
import io.snello.service.mail.SmtpMailService;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Singleton
public class MailProducer {


    @Inject
    Mailer mailer;

    @ConfigProperty(name = "snello.mailtype", defaultValue = "")
    String mailtype;

    public MailProducer() {
        Log.info("MailProducer");
    }

    @Produces
    public MailService db() throws Exception {
        Log.info("mailtype: " + mailtype);
        if (mailtype.equals("smtp")) {
            return new SmtpMailService(mailer);
        }
        throw new Exception("no mailtype");
    }
}
