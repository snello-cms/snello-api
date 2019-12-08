package io.snello.service.mail.smtp;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.snello.service.mail.Email;
import io.snello.service.mail.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.mail.*;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

import static io.snello.management.AppConstants.*;

@Singleton
@Requires(property = EMAIL_TYPE, value = "smtp")
public class SmtpEmailService implements EmailService {

    Logger logger = LoggerFactory.getLogger(SmtpEmailService.class);

    @Property(name = EMAIL_SMTP_PORT)
    String smtp_port;

    @Property(name = EMAIL_SMTP_AUTH)
    String smtp_auth;

    @Property(name = EMAIL_SMTP_STARTSSL_ENABLE)
    String starttls_enable;

    @Property(name = EMAIL_SMTP_HOST)
    String smtp_host;

    @Property(name = EMAIL_SMTP_USERNAME)
    String smtp_username;

    @Property(name = EMAIL_SMTP_PASSWORD)
    String smtp_password;

    @Property(name = EMAIL_MAIL_FROM)
    String mail_from;

    @Override
    public void send(Email email) throws Exception {

        // Step1
        logger.info("\n 1st ===> setup Mail Server Properties..");
        Properties mailServerProperties = System.getProperties();
        mailServerProperties.put("mail.smtp.host", smtp_host);
        mailServerProperties.put("mail.smtp.port", smtp_port);
        mailServerProperties.put("mail.smtp.auth", smtp_auth);
        mailServerProperties.put("mail.smtp.socketFactory.port", smtp_port);
        mailServerProperties.put("mail.smtp.starttls.enable", starttls_enable);
        mailServerProperties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        logger.info("Mail Server Properties have been setup successfully..");

        // Step2
        logger.info("\n\n 2nd ===> get Mail Session..");
        Session session = Session.getInstance(mailServerProperties,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(smtp_username, smtp_password);
                    }
                });
        MimeMessage mimeMessage = new MimeMessage(session);
        mimeMessage.setFrom(new InternetAddress(mail_from));
        mimeMessage.addRecipient(Message.RecipientType.TO, new InternetAddress(email.recipient));
        mimeMessage.setSubject(email.subject);
        mimeMessage.setContent(email.body, "text/html");
        logger.info("Mail Session has been created successfully..");

        // Step3
        logger.info("\n\n 3rd ===> Get Session and Send mail");
        Transport.send(mimeMessage);
    }

}
