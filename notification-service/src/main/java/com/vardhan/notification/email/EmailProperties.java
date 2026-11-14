package com.vardhan.notification.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(
        String provider,
        String fromAddress,
        String fromName,
        String appUrl,
        ZoneId timeZone,
        List<String> skipDomains,
        Brevo brevo,
        Dispatch dispatch) {

    public record Brevo(String apiKey) {
    }

    public record Dispatch(boolean enabled, long intervalMs, int batchSize, int maxAttempts,
                           long firstRetrySeconds, long leaseSeconds) {
    }

    /** Demo accounts use made-up addresses on a skipped domain; emailing them would only bounce. */
    public boolean shouldSkip(String address) {
        if (address == null || address.isBlank() || !address.contains("@")) {
            return true;
        }
        String domain = address.substring(address.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
        return skipDomains != null && skipDomains.stream()
                .anyMatch(skipped -> domain.equals(skipped.trim().toLowerCase(Locale.ROOT)));
    }
}
