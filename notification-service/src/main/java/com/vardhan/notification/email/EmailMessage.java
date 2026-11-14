package com.vardhan.notification.email;

/** One email, already rendered. The HTML part carries the same content as the plain-text part. */
public record EmailMessage(String to, String subject, String textBody, String htmlBody) {
}
