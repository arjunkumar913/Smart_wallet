package com.vardhan.wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// UserDetailsService auto-config is excluded: authentication is JWT-only, there are no in-memory users
@SpringBootApplication(scanBasePackages = {"com.vardhan.wallet", "com.vardhan.common"}, exclude = UserDetailsServiceAutoConfiguration.class)
public class WalletServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WalletServiceApplication.class, args);
    }
}
