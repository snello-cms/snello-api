package io.snello;

import org.junit.Test;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

public class MailTest {

    @Test
    public void mail() {
        final String username = "info@****************";
        final String password = "****************";

        Properties prop = new Properties();
        prop.put("mail.smtp.host", "****************");
        prop.put("mail.smtp.port", "465");
        prop.put("mail.smtp.auth", "true");
        prop.put("mail.smtp.socketFactory.port", "465");
        prop.put("mail.smtp.starttls.enable", "true");
        prop.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

        Session session = Session.getInstance(prop,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });

        try {

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress("info@snello.io"));
            message.setRecipients(
                    Message.RecipientType.TO,
                    InternetAddress.parse("fiorenzino@gmail.com")
            );
            message.setSubject("Testing Ovh SSL");
            message.setText("prova di invio email,"
                    + "\n\n Please do not spam my email!");

            Transport.send(message);

            System.out.println("Done");

        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }
}
