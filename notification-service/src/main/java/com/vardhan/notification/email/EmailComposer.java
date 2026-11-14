package com.vardhan.notification.email;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** Writes the emails. Plain text and HTML say the same thing; every value from an event is HTML-escaped. */
@Component
public class EmailComposer {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);

    private final EmailProperties properties;

    public EmailComposer(EmailProperties properties) {
        this.properties = properties;
    }

    public EmailMessage transferSent(String to, String recipientEmail, BigDecimal amount, String currency,
                                     UUID transactionId, Instant at) {
        String money = money(amount, currency);
        return compose(to,
                "You sent " + money + " to " + recipientEmail,
                money + " has left your Smart Wallet.",
                new String[][] {{"Amount", money}, {"To", recipientEmail}, {"When", when(at)}, {"Reference", transactionId.toString()}},
                "If you did not make this payment, sign in and check your passbook straight away.");
    }

    public EmailMessage transferReceived(String to, String senderEmail, BigDecimal amount, String currency,
                                         UUID transactionId, Instant at) {
        String money = money(amount, currency);
        return compose(to,
                "You received " + money + " from " + senderEmail,
                money + " has arrived in your Smart Wallet.",
                new String[][] {{"Amount", money}, {"From", senderEmail}, {"When", when(at)}, {"Reference", transactionId.toString()}},
                null);
    }

    public EmailMessage topUpCompleted(String to, BigDecimal amount, String currency, String provider,
                                       UUID transactionId, Instant at) {
        String money = money(amount, currency);
        return compose(to,
                money + " added to your Smart Wallet",
                "Your payment went through and " + money + " is now in your wallet.",
                new String[][] {{"Amount", money}, {"Paid through", providerName(provider)}, {"When", when(at)},
                        {"Reference", transactionId.toString()}},
                null);
    }

    /** ₹1,50,000.00: Indian digit grouping (lakh, crore), which java.text.NumberFormat does not do. */
    static String money(BigDecimal amount, String currency) {
        String plain = amount.setScale(2, RoundingMode.HALF_UP).abs().toPlainString();
        String whole = plain.substring(0, plain.indexOf('.'));
        String grouped;
        if (whole.length() <= 3) {
            grouped = whole;
        } else {
            String head = whole.substring(0, whole.length() - 3);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < head.length(); i++) {
                if (i > 0 && (head.length() - i) % 2 == 0) {
                    out.append(',');
                }
                out.append(head.charAt(i));
            }
            grouped = out + "," + whole.substring(whole.length() - 3);
        }
        String figure = (amount.signum() < 0 ? "-" : "") + grouped + plain.substring(plain.indexOf('.'));
        return "INR".equals(currency) ? "₹" + figure : (currency == null ? "" : currency + " ") + figure;
    }

    private String when(Instant at) {
        Instant moment = at == null ? Instant.now() : at;
        var local = moment.atZone(properties.timeZone());
        String zone = local.getZone().getId().equals("Asia/Kolkata") ? "IST" : local.getZone().getId();
        return WHEN.format(local).replace("AM", "am").replace("PM", "pm") + " " + zone;
    }

    private static String providerName(String provider) {
        if (provider == null) {
            return "Payment gateway";
        }
        return switch (provider) {
            case "RAZORPAY" -> "Razorpay";
            case "MOCK" -> "Demo Bank";
            default -> provider;
        };
    }

    private EmailMessage compose(String to, String subject, String lead, String[][] details, String warning) {
        String url = properties.appUrl();

        StringBuilder text = new StringBuilder().append(lead).append("\n\n");
        for (String[] row : details) {
            text.append(row[0]).append(": ").append(row[1]).append('\n');
        }
        text.append("\nSee it in your passbook: ").append(url).append('\n');
        if (warning != null) {
            text.append('\n').append(warning).append('\n');
        }
        text.append("\nYou get this email because you have a Smart Wallet account.\n");

        StringBuilder rows = new StringBuilder();
        for (String[] row : details) {
            rows.append("<tr><td style=\"padding:6px 16px 6px 0;color:#4a5876;white-space:nowrap\">")
                    .append(esc(row[0])).append("</td><td style=\"padding:6px 0;color:#14213d\">")
                    .append(esc(row[1])).append("</td></tr>");
        }
        String html = """
                <!doctype html>
                <html><body style="margin:0;background:#e6ebf3;font-family:Arial,Helvetica,sans-serif;color:#14213d">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="padding:24px 12px">
                <tr><td align="center">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:520px;background:#fbfcfe;border:1px solid #c5cfde;border-radius:6px">
                <tr><td style="padding:24px 24px 8px;font-size:18px;font-weight:bold">Smart Wallet</td></tr>
                <tr><td style="padding:8px 24px 16px;font-size:16px;line-height:1.5">%s</td></tr>
                <tr><td style="padding:0 24px 16px"><table role="presentation" cellpadding="0" cellspacing="0" style="font-size:14px;border-top:1px solid #dde4ee;width:100%%">%s</table></td></tr>
                <tr><td style="padding:0 24px 20px"><a href="%s" style="display:inline-block;background:#2443c9;color:#ffffff;text-decoration:none;font-weight:bold;padding:10px 18px;border-radius:6px">See it in your passbook</a></td></tr>
                %s
                <tr><td style="padding:16px 24px 24px;font-size:12px;color:#4a5876;border-top:1px solid #dde4ee">You get this email because you have a Smart Wallet account.</td></tr>
                </table></td></tr></table></body></html>
                """.formatted(esc(lead), rows, esc(url),
                warning == null ? "" : "<tr><td style=\"padding:0 24px 20px;font-size:14px;color:#8a1c16\">" + esc(warning) + "</td></tr>");

        return new EmailMessage(to, subject, text.toString(), html);
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
