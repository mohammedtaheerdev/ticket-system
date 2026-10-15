package com.ticket.system.security;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrganizationRegistrationRateLimiter {
    private static final int MAX_REGISTRATIONS = 5;
    private static final int MAX_TRACKED_CLIENTS = 10_000;
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private final Map<String, RegistrationWindow> registrationsByClient = new HashMap<>();

    public synchronized void checkAndRecord(String clientAddress) {
        Instant now = Instant.now();
        registrationsByClient.entrySet().removeIf(entry ->
                !now.isBefore(entry.getValue().startedAt().plus(WINDOW)));

        RegistrationWindow window = registrationsByClient.get(clientAddress);
        if (window != null && window.registrations() >= MAX_REGISTRATIONS) {
            throw tooManyRegistrations();
        }
        if (window == null && registrationsByClient.size() >= MAX_TRACKED_CLIENTS) {
            throw tooManyRegistrations();
        }

        if (window == null) {
            registrationsByClient.put(clientAddress, new RegistrationWindow(now, 1));
        } else {
            registrationsByClient.put(clientAddress,
                    new RegistrationWindow(window.startedAt(), window.registrations() + 1));
        }
    }

    private ResponseStatusException tooManyRegistrations() {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Too many organization creation attempts. Try again in 5 minutes.");
    }

    private record RegistrationWindow(Instant startedAt, int registrations) {
    }
}
