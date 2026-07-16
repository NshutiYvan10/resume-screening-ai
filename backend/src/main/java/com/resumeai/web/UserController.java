package com.resumeai.web;

import com.resumeai.domain.enums.Role;
import com.resumeai.domain.enums.UserStatus;
import com.resumeai.dto.CommonDtos.PageResponse;
import com.resumeai.dto.UserDtos.UserResponse;
import com.resumeai.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','COMPANY_ADMIN')")
    public PageResponse<UserResponse> list(@RequestParam(required = false) UUID companyId,
                                           @RequestParam(required = false) Role role,
                                           @RequestParam(required = false) UserStatus status,
                                           @RequestParam(required = false) String search,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return userService.list(companyId, role, status, search, page, size);
    }

    /** Active team members of the caller's company — used for interview panel selection. */
    @GetMapping("/team")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public List<UserResponse> team() {
        return userService.activeTeamMembers();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','COMPANY_ADMIN')")
    public UserResponse get(@PathVariable UUID id) {
        return userService.get(id);
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','COMPANY_ADMIN')")
    public UserResponse disable(@PathVariable UUID id) {
        return userService.setStatus(id, UserStatus.DISABLED);
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','COMPANY_ADMIN')")
    public UserResponse enable(@PathVariable UUID id) {
        return userService.setStatus(id, UserStatus.ACTIVE);
    }
}
