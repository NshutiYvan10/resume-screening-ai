package com.resumeai.web;

import com.resumeai.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/platform")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> platform() {
        return analyticsService.platform();
    }

    @GetMapping("/company")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public Map<String, Object> company() {
        return analyticsService.company();
    }
}
