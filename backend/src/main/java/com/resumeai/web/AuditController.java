package com.resumeai.web;

import com.resumeai.common.exception.ApiException;
import com.resumeai.domain.enums.Role;
import com.resumeai.dto.CommonDtos.AuditLogResponse;
import com.resumeai.dto.CommonDtos.PageResponse;
import com.resumeai.repository.AuditLogRepository;
import com.resumeai.security.SecurityUtils;
import com.resumeai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    /**
     * Audit trail. Super admin sees everything (optionally filtered by company);
     * company admins are hard-scoped to their own company's trail.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','COMPANY_ADMIN')")
    public PageResponse<AuditLogResponse> list(
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        UUID effectiveCompanyId = companyId;
        if (actor.getRole() != Role.SUPER_ADMIN) {
            effectiveCompanyId = actor.getCompanyId();
            if (effectiveCompanyId == null) {
                throw ApiException.forbidden("You are not associated with a company");
            }
        }

        return PageResponse.of(
                auditLogRepository.search(effectiveCompanyId, emptyToNull(action), emptyToNull(search),
                        from, to, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))),
                AuditLogResponse::from);
    }

    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
