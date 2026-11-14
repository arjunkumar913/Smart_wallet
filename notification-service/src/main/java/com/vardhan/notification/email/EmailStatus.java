package com.vardhan.notification.email;

/**
 * PENDING  waiting to be sent (first try or a retry after a failure).
 * SENDING  claimed by a dispatcher; if that dispatcher dies, the lease expires and it is claimed again.
 * SENT     accepted by the email provider. Final.
 * FAILED   gave up: rejected by the provider, or out of attempts. Final.
 */
public enum EmailStatus {
    PENDING, SENDING, SENT, FAILED
}
