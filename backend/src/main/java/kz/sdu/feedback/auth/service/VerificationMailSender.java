package kz.sdu.feedback.auth.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VerificationMailSender {
    private final ObjectProvider<JavaMailSender> senders;
    private final String host;
    private final String from;

    public VerificationMailSender(ObjectProvider<JavaMailSender> senders,
                                  @Value("${spring.mail.host:}") String host,
                                  @Value("${app.verification.from:}") String from) {
        this.senders = senders;
        this.host = host;
        this.from = from;
    }

    public void send(String email, String code) {
        var sender = senders.getIfAvailable();
        if (host.isBlank() || from.isBlank() || sender == null) throw unavailable();
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Verify your AI Student Feedback account");
        message.setText("Your verification code is: " + code + "\n\nThe code expires in 10 minutes."
                + "\nIf you did not request this code, you can ignore this email.");
        try {
            sender.send(message);
        } catch (MailException exception) {
            // Mail exceptions can contain message contents: never log or expose the exception.
            throw unavailable();
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Verification email could not be sent. Please try again later.");
    }
}
