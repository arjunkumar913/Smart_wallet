package com.vardhan.notification.email;

/**
 * A send that did not go through. {@code retryable} is false when sending again cannot help
 * (bad address, bad API key, rejected content), so the email is failed at once instead of retried.
 */
public class EmailSendException extends RuntimeException {

    private final boolean retryable;

    public EmailSendException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public EmailSendException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
