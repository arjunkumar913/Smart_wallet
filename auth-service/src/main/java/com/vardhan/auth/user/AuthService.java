package com.vardhan.auth.user;

import com.vardhan.auth.web.AuthResponse;
import com.vardhan.common.events.UserRegisteredEvent;
import com.vardhan.common.security.JwtService;
import com.vardhan.common.web.BusinessException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ApplicationEventPublisher eventPublisher;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Creates the user and raises a UserRegisteredEvent. The event is only sent to RabbitMQ
     * after this transaction commits (see UserEventPublisher), so wallet-service never
     * creates a wallet for a user that was rolled back.
     */
    @Transactional
    public AuthResponse register(String email, String rawPassword) {
        User user = createUser(email, rawPassword, Role.USER);
        return toResponse(user);
    }

    @Transactional
    public User createUser(String email, String rawPassword, Role role) {
        String normalizedEmail = normalize(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "An account with this email already exists");
        }
        User user = userRepository.save(new User(normalizedEmail, passwordEncoder.encode(rawPassword), role));
        eventPublisher.publishEvent(UserRegisteredEvent.of(user.getId(), user.getEmail()));
        return user;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(String email, String rawPassword) {
        User user = userRepository.findByEmail(normalize(email))
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPasswordHash()))
                // same error for unknown email and wrong password: don't reveal which emails exist
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                        "Email or password is incorrect"));
        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        return new AuthResponse(token, "Bearer", jwtService.expirationMinutes(),
                user.getId(), user.getEmail(), user.getRole().name());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
