package com.fixflow.notify;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class MailGateway {

    private final FixFlowProperties properties;
    private final JavaMailSender mailSender;

    public MailGateway(FixFlowProperties properties, ObjectProvider<JavaMailSender> mailSender) {
        this.properties = properties;
        this.mailSender = mailSender.getIfAvailable();
    }

    public boolean configured() {
        return properties.getMail().configured() && mailSender != null
                && StringUtils.hasText(properties.getMail().getFrom());
    }

    public void send(String to, String subject, String body) {
        if (!configured()) {
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE,
                    "Email is not configured. Set SMTP_HOST, SMTP_FROM, SMTP_USERNAME and SMTP_PASSWORD.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMail().getFrom());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        log.info("Sent email to {} ({})", to, subject);
    }
}
