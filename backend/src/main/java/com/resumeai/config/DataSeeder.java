package com.resumeai.config;

import com.resumeai.domain.User;
import com.resumeai.domain.enums.Role;
import com.resumeai.domain.enums.UserStatus;
import com.resumeai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures a platform super admin exists on first boot. Credentials come from
 * app.seed.* properties (SEED_ADMIN_EMAIL / SEED_ADMIN_PASSWORD env vars).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) {
        if (userRepository.existsByRole(Role.SUPER_ADMIN)) {
            return;
        }
        AppProperties.Seed seed = properties.getSeed();
        User admin = User.builder()
                .email(seed.getAdminEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(seed.getAdminPassword()))
                .fullName(seed.getAdminName())
                .role(Role.SUPER_ADMIN)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();
        userRepository.save(admin);
        log.warn("Seeded platform super admin account: {} (change the default password immediately)",
                seed.getAdminEmail());
    }
}
