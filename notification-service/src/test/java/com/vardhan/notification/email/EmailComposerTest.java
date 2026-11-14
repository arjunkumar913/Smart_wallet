package com.vardhan.notification.email;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmailComposerTest {

    private final EmailComposer composer = new EmailComposer(TestEmailProperties.defaults());
    private final UUID txId = UUID.fromString("1f1a12fc-5b6d-47e3-9413-ad5e3f7ad3a4");
    private final Instant at = Instant.parse("2026-11-14T07:13:00Z");   // 12:43 pm in India

    @Test
    void sentEmail_namesTheRecipientAmountTimeAndReference() {
        EmailMessage email = composer.transferSent("me@test.com", "friend@test.com", new BigDecimal("12500.00"), "INR", txId, at);

        assertThat(email.to()).isEqualTo("me@test.com");
        assertThat(email.subject()).isEqualTo("You sent ₹12,500.00 to friend@test.com");
        assertThat(email.textBody())
                .contains("Amount: ₹12,500.00", "To: friend@test.com", "When: 14 Nov 2026, 12:43 pm IST",
                        "Reference: " + txId, "https://wallet.example", "If you did not make this payment");
        assertThat(email.htmlBody()).contains("₹12,500.00", "friend@test.com", "href=\"https://wallet.example\"");
    }

    @Test
    void receivedEmail_namesTheSender() {
        EmailMessage email = composer.transferReceived("friend@test.com", "me@test.com", new BigDecimal("320.00"), "INR", txId, at);

        assertThat(email.subject()).isEqualTo("You received ₹320.00 from me@test.com");
        assertThat(email.textBody()).contains("From: me@test.com").doesNotContain("If you did not make this payment");
    }

    @Test
    void indianDigitGrouping_isUsedForLakhs() {
        assertThat(EmailComposer.money(new BigDecimal("150000.00"), "INR")).isEqualTo("₹1,50,000.00");
        assertThat(EmailComposer.money(new BigDecimal("12345678.5"), "INR")).isEqualTo("₹1,23,45,678.50");
        assertThat(EmailComposer.money(new BigDecimal("999"), "INR")).isEqualTo("₹999.00");
        assertThat(EmailComposer.money(new BigDecimal("1000"), "INR")).isEqualTo("₹1,000.00");
        assertThat(EmailComposer.money(new BigDecimal("2500.75"), "USD")).isEqualTo("USD 2,500.75");
    }

    @Test
    void topUpEmail_namesThePaymentGateway() {
        EmailMessage email = composer.topUpCompleted("me@test.com", new BigDecimal("500.00"), "INR", "RAZORPAY", txId, at);

        assertThat(email.subject()).isEqualTo("₹500.00 added to your Smart Wallet");
        assertThat(email.textBody()).contains("Paid through: Razorpay");
    }

    @Test
    void valuesFromEvents_areEscapedInHtml() {
        EmailMessage email = composer.transferReceived("friend@test.com", "<script>alert(1)</script>@x.com",
                new BigDecimal("1.00"), "INR", txId, at);

        assertThat(email.htmlBody()).doesNotContain("<script>").contains("&lt;script&gt;");
    }
}
