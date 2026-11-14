package com.vardhan.notification.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailPropertiesTest {

    private final EmailProperties properties = TestEmailProperties.defaults();

    @Test
    void demoAccounts_andMalformedAddresses_areSkipped() {
        assertThat(properties.shouldSkip("guest-1c5d5f38@demo.smartwallet.dev")).isTrue();
        assertThat(properties.shouldSkip("Guest@DEMO.SmartWallet.dev")).isTrue();
        assertThat(properties.shouldSkip("not-an-address")).isTrue();
        assertThat(properties.shouldSkip("")).isTrue();
        assertThat(properties.shouldSkip(null)).isTrue();
    }

    @Test
    void realAddresses_areNotSkipped() {
        assertThat(properties.shouldSkip("vardhan@gmail.com")).isFalse();
        assertThat(properties.shouldSkip("someone@notdemo.smartwallet.dev")).isFalse();
    }
}
