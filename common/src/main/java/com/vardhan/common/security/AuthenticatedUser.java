package com.vardhan.common.security;

/**
 * The caller identity extracted from a valid JWT. Available in controllers via
 * {@code @AuthenticationPrincipal AuthenticatedUser user}.
 */
public record AuthenticatedUser(Long userId, String email, String role) {
}
