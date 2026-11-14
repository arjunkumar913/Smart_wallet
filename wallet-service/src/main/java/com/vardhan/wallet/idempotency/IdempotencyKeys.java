package com.vardhan.wallet.idempotency;

import com.vardhan.common.web.BusinessException;
import org.springframework.http.HttpStatus;

/** The one rule for what a valid Idempotency-Key header looks like, shared by transfers and top-ups. */
public final class IdempotencyKeys {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private static final int MAX_LENGTH = 100;

    private IdempotencyKeys() {
    }

    public static String validate(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MISSING_IDEMPOTENCY_KEY", "Idempotency-Key header is required");
        }
        String trimmed = key.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must be at most " + MAX_LENGTH + " characters");
        }
        return trimmed;
    }

    public static BusinessException inProgress() {
        return new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_IN_PROGRESS",
                "A request with this Idempotency-Key is already being processed; retry shortly");
    }

    public static BusinessException reused() {
        return new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                "This Idempotency-Key was already used for a different request");
    }
}
