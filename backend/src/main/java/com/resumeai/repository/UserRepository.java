package com.resumeai.repository;

import com.resumeai.domain.User;
import com.resumeai.domain.enums.Role;
import com.resumeai.domain.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);

    List<User> findByCompanyIdAndRoleIn(UUID companyId, List<Role> roles);

    long countByRole(Role role);

    long countByCompanyId(UUID companyId);

    @Query("""
            SELECT u FROM User u LEFT JOIN u.company c
            WHERE (:companyId IS NULL OR c.id = :companyId)
              AND (:role IS NULL OR u.role = :role)
              AND (:status IS NULL OR u.status = :status)
              AND (:search IS NULL OR lower(u.fullName) LIKE lower(concat('%', cast(:search as string), '%'))
                   OR lower(u.email) LIKE lower(concat('%', cast(:search as string), '%')))
            """)
    Page<User> search(@Param("companyId") UUID companyId,
                      @Param("role") Role role,
                      @Param("status") UserStatus status,
                      @Param("search") String search,
                      Pageable pageable);
}
