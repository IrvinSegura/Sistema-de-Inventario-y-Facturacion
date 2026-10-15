package com.irvin.inventory.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionTimedOutException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void timeoutsAreRetryableServiceUnavailable() {
        for (Exception timeout : new Exception[] {
                new TransactionTimedOutException("Transaction timed out"),
                new QueryTimeoutException("Statement cancelled")}) {
            ResponseEntity<ProblemDetail> response = handler.handleTimeout(timeout);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
            assertThat(response.getBody().getDetail()).isEqualTo("The resource is busy, please retry");
        }
    }
}
