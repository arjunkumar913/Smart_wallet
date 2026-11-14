package com.vardhan.notification.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Default sender: writes each email to the log, so the whole flow works locally without an email account. */
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "log", matchIfMissing = true)
public class LogEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

    @Override
    public String send(EmailMessage message) {
        String id = "log-" + UUID.randomUUID();
        log.info("""
                Email {} (EMAIL_PROVIDER=log, nothing was actually sent)
                To: {}
                Subject: {}

                {}""", id, message.to(), message.subject(), message.textBody());
        return id;
    }
}
