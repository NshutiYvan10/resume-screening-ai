package com.resumeai.web;

import com.resumeai.dto.CommonDtos.NotificationResponse;
import com.resumeai.dto.CommonDtos.PageResponse;
import com.resumeai.security.SecurityUtils;
import com.resumeai.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public PageResponse<NotificationResponse> list(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "15") int size) {
        return notificationService.list(SecurityUtils.requireCurrentUser().getId(), page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notificationService.unreadCount(SecurityUtils.requireCurrentUser().getId()));
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable UUID id) {
        notificationService.markRead(SecurityUtils.requireCurrentUser().getId(), id);
    }

    @PostMapping("/read-all")
    public void markAllRead() {
        notificationService.markAllRead(SecurityUtils.requireCurrentUser().getId());
    }
}
