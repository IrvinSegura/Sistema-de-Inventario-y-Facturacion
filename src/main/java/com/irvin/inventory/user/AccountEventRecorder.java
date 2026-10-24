package com.irvin.inventory.user;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import com.irvin.inventory.user.dto.AccountEventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// The only writer of account_event, used by UserService and PasswordResetService in the transaction
// of the change it records.
@Component
@RequiredArgsConstructor
public class AccountEventRecorder {

    private final AccountEventRepository accountEventRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    // References, not loaded users: a @Modifying query may have cleared the persistence context.
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long userId, Long actorId, AccountEventType type, String oldValue, String newValue) {
        accountEventRepository.save(new AccountEvent(userRepository.getReferenceById(userId),
                userRepository.getReferenceById(actorId), type, oldValue, newValue,
                clock.instant().truncatedTo(ChronoUnit.MICROS)));
    }

    // Always newest first: a client sort would break the history's order (or name a missing field).
    @Transactional(readOnly = true)
    public Page<AccountEventResponse> findForUser(Long userId, Pageable pageable) {
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return accountEventRepository.findByUserIdOrderByOccurredAtDescIdDesc(userId, unsorted)
                .map(AccountEventResponse::from);
    }
}
