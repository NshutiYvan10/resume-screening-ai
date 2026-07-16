package com.resumeai.service;

import com.resumeai.domain.Application;
import com.resumeai.domain.ApplicationEvent;
import com.resumeai.domain.enums.ApplicationEventType;
import com.resumeai.repository.ApplicationEventRepository;
import com.resumeai.security.SecurityUtils;
import com.resumeai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Records the per-candidate activity timeline. Every pipeline action lands
 * here with its actor, so the full history of a candidate's journey is
 * transparent and attributable.
 */
@Service
@RequiredArgsConstructor
public class ApplicationEventService {

    private final ApplicationEventRepository eventRepository;

    @Transactional
    public void record(Application application, ApplicationEventType type, Map<String, Object> details) {
        UserPrincipal actor = SecurityUtils.currentUser().orElse(null);
        eventRepository.save(ApplicationEvent.builder()
                .application(application)
                .actorId(actor != null ? actor.getId() : null)
                .actorName(actor != null ? actor.getFullName() : "System")
                .type(type)
                .details(details)
                .build());
    }

    @Transactional(readOnly = true)
    public List<ApplicationEvent> timeline(UUID applicationId) {
        return eventRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
    }
}
