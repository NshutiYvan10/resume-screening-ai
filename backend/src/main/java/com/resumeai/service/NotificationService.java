package com.resumeai.service;

import com.resumeai.common.exception.ApiException;
import com.resumeai.domain.Notification;
import com.resumeai.domain.User;
import com.resumeai.domain.enums.NotificationType;
import com.resumeai.dto.CommonDtos.NotificationResponse;
import com.resumeai.dto.CommonDtos.PageResponse;
import com.resumeai.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;

    /**
     * Create an in-app notification and optionally mirror it to email.
     */
    @Transactional
    public void notify(User user, NotificationType type, String title, String message,
                       String link, boolean sendEmail) {
        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .message(message)
                .link(link)
                .emailSent(sendEmail)
                .build();
        notificationRepository.save(notification);

        if (sendEmail) {
            String url = link != null ? emailService.frontendUrl(link) : null;
            String html = emailService.template(title,
                    "<p>Hi " + user.getFullName() + ",</p><p>" + message + "</p>",
                    url != null ? "View details" : null, url);
            emailService.send(user.getEmail(), title, html, url);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID userId, int page, int size) {
        return PageResponse.of(
                notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size)),
                NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> ApiException.notFound("Notification not found"));
        if (!notification.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("Not your notification");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllRead(userId, Instant.now());
    }
}
