package com.vardhan.notification.notification;

import com.vardhan.common.security.AuthenticatedUser;
import com.vardhan.common.web.BusinessException;
import com.vardhan.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
@Validated
public class NotificationController {

    private final NotificationRepository repository;

    public NotificationController(NotificationRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Operation(summary = "My notifications, newest first")
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @RequestParam(defaultValue = "0") @Min(0) int page,
                                                   @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(repository.findByUserIdOrderByCreatedAtDescIdDesc(user.userId(), PageRequest.of(page, size)),
                NotificationResponse::from);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "How many of my notifications are unread")
    @Transactional(readOnly = true)
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AuthenticatedUser user) {
        return Map.of("unread", repository.countByUserIdAndReadAtIsNull(user.userId()));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark one of my notifications as read")
    @Transactional
    public NotificationResponse markRead(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        // looking up by id AND owner means another user's notification is simply "not found"
        Notification notification = repository.findByIdAndUserId(id, user.userId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Notification not found"));
        notification.markRead();
        return NotificationResponse.from(notification);
    }
}
