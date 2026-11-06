package com.vardhan.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-definitely-at-least-32-bytes";

    private final JwtService jwtService = new JwtService(SECRET, 60);

    @Test
    void generatedToken_parsesBackToSameUser() {
        String token = jwtService.generateToken(42L, "vardhan@example.com", "USER");

        AuthenticatedUser user = jwtService.parse(token);

        assertThat(user.userId()).isEqualTo(42L);
        assertThat(user.email()).isEqualTo("vardhan@example.com");
        assertThat(user.role()).isEqualTo("USER");
    }

    @Test
    void tamperedToken_isRejected() {
        String token = jwtService.generateToken(42L, "vardhan@example.com", "USER");
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> jwtService.parse(tampered))
                .isInstanceOf(JwtService.InvalidTokenException.class);
    }

    @Test
    void tokenSignedWithAnotherSecret_isRejected() {
        JwtService other = new JwtService("another-secret-that-is-also-at-least-32-bytes", 60);
        String foreignToken = other.generateToken(1L, "x@example.com", "ADMIN");

        assertThatThrownBy(() -> jwtService.parse(foreignToken))
                .isInstanceOf(JwtService.InvalidTokenException.class);
    }

    @Test
    void expiredToken_isRejected() {
        JwtService alreadyExpired = new JwtService(SECRET, -1); // already expired when issued
        String token = alreadyExpired.generateToken(1L, "x@example.com", "USER");

        assertThatThrownBy(() -> jwtService.parse(token))
                .isInstanceOf(JwtService.InvalidTokenException.class);
    }

    @Test
    void shortSecret_failsFast() {
        assertThatThrownBy(() -> new JwtService("too-short", 60))
                .isInstanceOf(IllegalStateException.class);
    }
}
