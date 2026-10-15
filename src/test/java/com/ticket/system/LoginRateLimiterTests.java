package com.ticket.system;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ticket.system.security.LoginRateLimiter;
import com.ticket.system.security.OrganizationRegistrationRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class LoginRateLimiterTests {
    @Test
    void blocksAfterFiveFailuresAndAllowsAttemptsAfterSuccessfulLogin() {
        LoginRateLimiter rateLimiter = new LoginRateLimiter();
        String clientAddress = "192.0.2.1";

        for (int attempt = 0; attempt < 5; attempt++) {
            assertDoesNotThrow(() -> rateLimiter.checkAllowed(clientAddress));
            rateLimiter.recordFailure(clientAddress);
        }

        ResponseStatusException blocked = assertThrows(ResponseStatusException.class,
                () -> rateLimiter.checkAllowed(clientAddress));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, blocked.getStatusCode());

        rateLimiter.recordSuccess(clientAddress);

        assertDoesNotThrow(() -> rateLimiter.checkAllowed(clientAddress));
    }

    @Test
    void limitsOrganizationCreationAttemptsPerClientIp() {
        OrganizationRegistrationRateLimiter rateLimiter = new OrganizationRegistrationRateLimiter();
        String clientAddress = "192.0.2.2";

        for (int attempt = 0; attempt < 5; attempt++) {
            assertDoesNotThrow(() -> rateLimiter.checkAndRecord(clientAddress));
        }

        ResponseStatusException blocked = assertThrows(ResponseStatusException.class,
                () -> rateLimiter.checkAndRecord(clientAddress));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, blocked.getStatusCode());
    }
}
