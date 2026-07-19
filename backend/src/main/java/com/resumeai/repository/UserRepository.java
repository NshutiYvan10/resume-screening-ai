package com.resumeai.repository;

import com.resumeai.domain.User;
import com.resumeai.domain.enums.Role;
import com.resumeai.domain.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    /** Loads the user with its company initialized (used by the auth filter, which has no session). */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.company WHERE u.id = :id")
    Optional<User> findByIdWithCompany(@Param("id") UUID id);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);

    List<User> findByCompanyIdAndRoleIn(UUID companyId, List<Role> roles);

    long countByRole(Role role);

    long countByCompanyId(UUID companyId);

    /** Platform growth: new user signups grouped by month [YYYY-MM, count]. */
    @Query(value = """
            SELECT to_char(date_trunc('month', created_at), 'YYYY-MM') AS m, count(*) AS c
            FROM users
            GROUP BY 1 ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> newUsersByMonth();

    long countByCompanyIdAndRoleAndStatus(UUID companyId, Role role, UserStatus status);

    /**
     * Locks the company's matching users FOR UPDATE so the last-active-admin check and the
     * subsequent status change are atomic against concurrent disables (avoids a TOCTOU race).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.company.id = :companyId AND u.role = :role AND u.status = :status")
    List<User> lockByCompanyAndRoleAndStatus(@Param("companyId") UUID companyId,
                                             @Param("role") Role role,
                                             @Param("status") UserStatus status);

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
