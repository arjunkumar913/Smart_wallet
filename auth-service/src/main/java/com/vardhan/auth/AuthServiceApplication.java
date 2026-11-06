package com.vardhan.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// UserDetailsService auto-config is excluded: authentication is JWT-only, there are no in-memory users
@SpringBootApplication(scanBasePackages = {"com.vardhan.auth", "com.vardhan.common"}, exclude = UserDetailsServiceAutoConfiguration.class)
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
