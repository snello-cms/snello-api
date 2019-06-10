package io.snello.service.mail;

public class Email {
    
    public String recipient;
    public String subject;
    public String body;

    public Email(String recipient, String subject, String body) {
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
    }
}
