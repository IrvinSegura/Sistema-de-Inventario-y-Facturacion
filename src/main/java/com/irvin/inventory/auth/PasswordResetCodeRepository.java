package com.irvin.inventory.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

// Callers hold the user's row lock, so a user's codes are never changed concurrently.
public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    Optional<PasswordResetCode> findFirstByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    long countByUserIdAndCreatedAtAfter(Long userId, Instant since);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PasswordResetCode c set c.invalidatedAt = :now
            where c.user.id = :userId and c.invalidatedAt is null""")
    int invalidateAllByUserId(Long userId, Instant now);

    @Modifying
    @Query("delete from PasswordResetCode c where c.createdAt < :cutoff")
    int deleteCreatedBefore(Instant cutoff);
}
