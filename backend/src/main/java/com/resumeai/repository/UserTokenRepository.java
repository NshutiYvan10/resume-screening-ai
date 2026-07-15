package com.resumeai.repository;

import com.resumeai.domain.UserToken;
import com.resumeai.domain.enums.UserTokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserTokenRepository extends JpaRepository<UserToken, UUID> {

    Optional<UserToken> findByTokenHashAndType(String tokenHash, UserTokenType type);

    @Modifying
    @Query("DELETE FROM UserToken t WHERE t.user.id = :userId AND t.type = :type")
    void deleteByUserIdAndType(@Param("userId") UUID userId, @Param("type") UserTokenType type);
}
