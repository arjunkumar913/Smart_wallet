package com.vardhan.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// UserDetailsService auto-config is excluded: authentication is JWT-only, there are no in-memory users
@SpringBootApplication(scanBasePackages = {"com.vardhan.notification", "com.vardhan.common"}, exclude = UserDetailsServiceAutoConfiguration.class)
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
