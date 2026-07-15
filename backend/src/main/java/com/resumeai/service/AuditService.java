package com.resumeai.service;

import com.resumeai.domain.AuditLog;
import com.resumeai.repository.AuditLogRepository;
import com.resumeai.security.SecurityUtils;
import com.resumeai.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Record an audit event attributed to the currently authenticated user.
     * Uses REQUIRES_NEW so audit entries survive even if the surrounding
     * transaction is later rolled back by an unrelated error, and never
     * breaks the main operation if audit persistence itself fails.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String entityType, String entityId, Map<String, Object> details) {
        try {
            UserPrincipal actor = SecurityUtils.currentUser().orElse(null);
            log(actor, actor != null ? actor.getCompanyId() : null, action, entityType, entityId, details);
        } catch (Exception e) {
            log.error("Failed to write audit log for action {}", action, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UserPrincipal actor, UUID companyId, String action, String entityType,
                    String entityId, Map<String, Object> details) {
        try {
            AuditLog entry = AuditLog.builder()
                    .actorId(actor != null ? actor.getId() : null)
                    .actorEmail(actor != null ? actor.getEmail() : "system")
                    .actorRole(actor != null ? actor.getRole().name() : "SYSTEM")
                    .companyId(companyId)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .details(details)
                    .ipAddress(currentIp())
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to write audit log for action {}", action, e);
        }
    }

    private String currentIp() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                HttpServletRequest request = attrs.getRequest();
                String forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                    return forwarded.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
