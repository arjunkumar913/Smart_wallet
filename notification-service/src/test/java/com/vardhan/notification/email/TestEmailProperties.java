package com.vardhan.notification.email;

import java.time.ZoneId;
import java.util.List;

public final class TestEmailProperties {

    private TestEmailProperties() {
    }

    public static EmailProperties defaults() {
        return withDispatch(new EmailProperties.Dispatch(false, 5000, 20, 6, 30, 120));
    }

    public static EmailProperties withDispatch(EmailProperties.Dispatch dispatch) {
        return new EmailProperties("log", "no-reply@smartwallet.dev", "Smart Wallet", "https://wallet.example",
                ZoneId.of("Asia/Kolkata"), List.of("demo.smartwallet.dev"), new EmailProperties.Brevo("test-key"), dispatch);
    }
}
