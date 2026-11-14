package com.vardhan.notification.email;

/** Hands one email to a provider. Implementations: {@link LogEmailSender} (default) and {@link BrevoEmailSender}. */
public interface EmailSender {

    /** @return the provider's id for the message, kept for support and debugging */
    String send(EmailMessage message) throws EmailSendException;
}
