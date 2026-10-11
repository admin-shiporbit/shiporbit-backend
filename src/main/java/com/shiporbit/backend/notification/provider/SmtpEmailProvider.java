package com.shiporbit.backend.notification.provider;

import com.shiporbit.backend.exception.NotificationException;
import com.shiporbit.backend.notification.config.EmailConfigProperties;
import com.shiporbit.backend.notification.dto.NotificationResponse;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "notification.email.provider", havingValue = "smtp")
public class SmtpEmailProvider implements EmailProvider {

    private static final String NAME = "SMTP";

    private final JavaMailSender mailSender;
    private final EmailConfigProperties props;

    public SmtpEmailProvider(JavaMailSender mailSender, EmailConfigProperties props) {
        this.mailSender = mailSender;
        this.props = props;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public NotificationResponse send(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(props.from(), props.fromName());
            if (props.replyTo() != null && !props.replyTo().isBlank()) {
                helper.setReplyTo(props.replyTo());
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(message);
            return NotificationResponse.sent(NAME, message.getMessageID());
        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            throw new NotificationException("SMTP send failed: " + e.getMessage());
        }
    }
}
