package com.vardhan.auth.config;

import com.vardhan.auth.user.AuthService;
import com.vardhan.auth.user.Role;
import com.vardhan.auth.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Creates the first ADMIN from ADMIN_EMAIL / ADMIN_PASSWORD on startup if it does not exist yet.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AuthService authService;
    private final UserRepository userRepository;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrap(AuthService authService, UserRepository userRepository,
                          @Value("${app.admin.email:}") String adminEmail,
                          @Value("${app.admin.password:}") String adminPassword) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.info("No admin configured (app.admin.email/password empty); skipping admin bootstrap");
            return;
        }
        if (userRepository.existsByEmail(adminEmail.trim().toLowerCase(Locale.ROOT))) {
            return;
        }
        authService.createUser(adminEmail, adminPassword, Role.ADMIN);
        log.info("Created admin user {}", adminEmail);
    }
}
