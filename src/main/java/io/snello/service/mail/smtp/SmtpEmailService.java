package io.snello.service.mail.smtp;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.snello.service.mail.Email;
import io.snello.service.mail.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
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

    @Override
    public void send(Email email) throws AddressException, MessagingException {

        // Step1
        logger.info("\n 1st ===> setup Mail Server Properties..");
        Properties mailServerProperties = System.getProperties();
        //        mailServerProperties.put("mail.smtp.port", "587");
        //        mailServerProperties.put("mail.smtp.auth", "true");
        //        mailServerProperties.put("mail.smtp.starttls.enable", "true");
        //        transport.connect("smtp.gmail.com", "<----- Your GMAIL ID ----->", "<----- Your GMAIL PASSWORD ----->");
        mailServerProperties.put("mail.smtp.port", smtp_port);
        mailServerProperties.put("mail.smtp.auth", smtp_auth);
        mailServerProperties.put("mail.smtp.starttls.enable", starttls_enable);
        logger.info("Mail Server Properties have been setup successfully..");

        // Step2
        logger.info("\n\n 2nd ===> get Mail Session..");
        Session getMailSession = Session.getDefaultInstance(mailServerProperties, null);
        MimeMessage generateMailMessage = new MimeMessage(getMailSession);
        generateMailMessage.addRecipient(Message.RecipientType.TO, new InternetAddress(email.recipient));
        generateMailMessage.setSubject(email.subject);
        generateMailMessage.setContent(email.body, "text/html");
        logger.info("Mail Session has been created successfully..");

        // Step3
        logger.info("\n\n 3rd ===> Get Session and Send mail");
        Transport transport = getMailSession.getTransport("smtp");

        // Enter your correct gmail UserID and Password
        // if you have 2FA enabled then provide App Specific Password
        transport.connect(smtp_host, smtp_username, smtp_password);
        transport.sendMessage(generateMailMessage, generateMailMessage.getAllRecipients());
        transport.close();
    }

}
