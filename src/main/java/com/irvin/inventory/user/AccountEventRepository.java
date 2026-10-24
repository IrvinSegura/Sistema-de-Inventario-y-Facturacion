package com.irvin.inventory.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountEventRepository extends JpaRepository<AccountEvent, Long> {

    // Uses ix_account_event_user_date; id breaks ties between events of the same instant.
    @EntityGraph(attributePaths = "actor")
    Page<AccountEvent> findByUserIdOrderByOccurredAtDescIdDesc(Long userId, Pageable pageable);
}
