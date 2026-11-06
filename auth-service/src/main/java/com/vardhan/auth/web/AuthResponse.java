package com.vardhan.auth.web;

public record AuthResponse(String accessToken, String tokenType, long expiresInMinutes,
                           Long userId, String email, String role) {
}
