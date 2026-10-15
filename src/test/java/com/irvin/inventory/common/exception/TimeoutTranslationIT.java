package com.irvin.inventory.common.exception;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvin.inventory.AbstractIntegrationTest;
import com.irvin.inventory.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.support.TransactionTemplate;

// Real timeouts must reach GlobalExceptionHandler as the types it maps to 503, not as a generic 500.
class TimeoutTranslationIT extends AbstractIntegrationTest {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void statementCancelledByTheTimeoutIsAQueryTimeout() {
        // PostgreSQL cancels it with SQLState 57014.
        assertThatThrownBy(() -> inOneSecondTransaction(() -> jdbcTemplate.execute("SELECT pg_sleep(3)")))
                .isInstanceOf(QueryTimeoutException.class);
    }

    @Test
    void statementAfterTheDeadlineIsATransactionTimeout() {
        assertThatThrownBy(() -> inOneSecondTransaction(() -> {
            sleepPastTheDeadline();
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        })).isInstanceOf(TransactionTimedOutException.class);
        assertThatThrownBy(() -> inOneSecondTransaction(() -> {
            userRepository.count();
            sleepPastTheDeadline();
            userRepository.count();
        })).isInstanceOf(TransactionTimedOutException.class);
    }

    private void inOneSecondTransaction(Runnable body) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(1);
        transaction.executeWithoutResult(status -> body.run());
    }

    private static void sleepPastTheDeadline() {
        try {
            Thread.sleep(1_500);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
