package com.resumeai.dto;

import com.resumeai.domain.AuditLog;
import com.resumeai.domain.Notification;
import com.resumeai.domain.enums.NotificationType;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public final class CommonDtos {

    private CommonDtos() {
    }

    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {

        public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
            return new PageResponse<>(
                    page.getContent().stream().map(mapper).toList(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages());
        }
    }

    public record NotificationResponse(
            UUID id,
            NotificationType type,
            String title,
            String message,
            String link,
            boolean read,
            Instant createdAt) {

        public static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                    n.getLink(), n.getReadAt() != null, n.getCreatedAt());
        }
    }

    public record AuditLogResponse(
            Long id,
            UUID actorId,
            String actorEmail,
            String actorRole,
            UUID companyId,
            String action,
            String entityType,
            String entityId,
            Map<String, Object> details,
            String ipAddress,
            Instant createdAt) {

        public static AuditLogResponse from(AuditLog l) {
            return new AuditLogResponse(l.getId(), l.getActorId(), l.getActorEmail(), l.getActorRole(),
                    l.getCompanyId(), l.getAction(), l.getEntityType(), l.getEntityId(), l.getDetails(),
                    l.getIpAddress(), l.getCreatedAt());
        }
    }
}
