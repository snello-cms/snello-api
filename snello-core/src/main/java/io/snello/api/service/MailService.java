package io.snello.api.service;

import io.quarkus.mailer.Attachment;

import java.util.List;

public interface MailService {

    public void send(String subject, String body, List<String> to, List<String> cc, List<String> bcc, List<Attachment> attachments);
}
