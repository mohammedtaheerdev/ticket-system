package com.ticket.system.security;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LoginRateLimiter {
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int MAX_TRACKED_CLIENTS = 10_000;
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private final Map<String, AttemptWindow> attemptsByClient = new HashMap<>();

    public synchronized void checkAllowed(String clientAddress) {
        Instant now = Instant.now();
        removeExpiredWindows(now);
        AttemptWindow window = attemptsByClient.get(clientAddress);
        if (window != null && window.failedAttempts() >= MAX_FAILED_ATTEMPTS) {
            throw tooManyAttempts();
        }
        if (window == null && attemptsByClient.size() >= MAX_TRACKED_CLIENTS) {
            throw tooManyAttempts();
        }
    }

    public synchronized void recordFailure(String clientAddress) {
        Instant now = Instant.now();
        removeExpiredWindows(now);
        AttemptWindow window = attemptsByClient.get(clientAddress);
        if (window == null) {
            if (attemptsByClient.size() >= MAX_TRACKED_CLIENTS) {
                throw tooManyAttempts();
            }
            attemptsByClient.put(clientAddress, new AttemptWindow(now, 1));
        } else {
            attemptsByClient.put(clientAddress, new AttemptWindow(window.startedAt(),
                    window.failedAttempts() + 1));
        }
    }

    public synchronized void recordSuccess(String clientAddress) {
        attemptsByClient.remove(clientAddress);
    }

    private void removeExpiredWindows(Instant now) {
        attemptsByClient.entrySet().removeIf(entry ->
                !now.isBefore(entry.getValue().startedAt().plus(WINDOW)));
    }

    private ResponseStatusException tooManyAttempts() {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Too many failed login attempts. Try again in 5 minutes.");
    }

    private record AttemptWindow(Instant startedAt, int failedAttempts) {
    }
}
