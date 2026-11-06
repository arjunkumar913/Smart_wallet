package com.vardhan.auth.user;

import com.vardhan.auth.web.AuthResponse;
import com.vardhan.common.events.UserRegisteredEvent;
import com.vardhan.common.security.JwtService;
import com.vardhan.common.web.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private UserRepository userRepository;
    private ApplicationEventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4); // low cost keeps tests fast
    private final JwtService jwtService = new JwtService("test-secret-that-is-definitely-at-least-32-bytes", 60);
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        authService = new AuthService(userRepository, passwordEncoder, jwtService, eventPublisher);
    }

    @Test
    void register_normalizesEmail_hashesPassword_andPublishesEvent() {
        when(userRepository.existsByEmail("vardhan@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> withId(inv.getArgument(0), 7L));

        AuthResponse response = authService.register("  Vardhan@Example.com ", "secret-pass");

        assertThat(response.email()).isEqualTo("vardhan@example.com");
        assertThat(response.role()).isEqualTo("USER");
        assertThat(jwtService.parse(response.accessToken()).userId()).isEqualTo(7L);
        verify(eventPublisher).publishEvent(any(UserRegisteredEvent.class));
    }

    @Test
    void register_withTakenEmail_returnsConflict_andPublishesNothing() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("taken@example.com", "secret-pass"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("EMAIL_TAKEN");
                });
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void login_withWrongPassword_isUnauthorized() {
        User stored = withId(new User("vardhan@example.com", passwordEncoder.encode("right-pass"), Role.USER), 7L);
        when(userRepository.findByEmail("vardhan@example.com")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.login("vardhan@example.com", "wrong-pass"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_CREDENTIALS"));
    }

    @Test
    void login_withUnknownEmail_givesSameErrorAsWrongPassword() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("ghost@example.com", "whatever"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_CREDENTIALS"));
    }

    @Test
    void login_withCorrectPassword_returnsToken() {
        User stored = withId(new User("vardhan@example.com", passwordEncoder.encode("right-pass"), Role.ADMIN), 3L);
        when(userRepository.findByEmail("vardhan@example.com")).thenReturn(Optional.of(stored));

        AuthResponse response = authService.login("vardhan@example.com", "right-pass");

        assertThat(jwtService.parse(response.accessToken()).role()).isEqualTo("ADMIN");
    }

    private static User withId(User user, Long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
