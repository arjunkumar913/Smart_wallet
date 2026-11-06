package com.vardhan.common.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by auth-service after a user is committed; wallet-service creates the wallet from it.
 *
 * @param eventId unique per event, used by consumers to ignore duplicate deliveries
 */
public record UserRegisteredEvent(UUID eventId, Long userId, String email, Instant occurredAt) {

    public static UserRegisteredEvent of(Long userId, String email) {
        return new UserRegisteredEvent(UUID.randomUUID(), userId, email, Instant.now());
    }
}
