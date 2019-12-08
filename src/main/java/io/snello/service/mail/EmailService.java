package io.snello.service.mail;

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;

public interface EmailService {


    public void send(Email email) throws Exception;
}
