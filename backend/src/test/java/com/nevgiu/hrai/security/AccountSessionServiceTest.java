package com.nevgiu.hrai.security;

import org.junit.jupiter.api.Test;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountSessionServiceTest {
    @SuppressWarnings("unchecked")
    private final FindByIndexNameSessionRepository<Session> repository =
            mock(FindByIndexNameSessionRepository.class);
    private final AccountSessionService service = new AccountSessionService(repository);

    @Test
    void deletesEveryIndexedSessionForThePrincipal() {
        when(repository.findByPrincipalName("user@example.com"))
                .thenReturn(Map.of("session-1", mock(Session.class), "session-2", mock(Session.class)));

        assertThat(service.revoke("user@example.com")).isEqualTo(2);
        verify(repository).deleteById("session-1");
        verify(repository).deleteById("session-2");
    }

    @Test
    void expiresTheOldestSessionToMakeRoomForANewLogin() {
        Session oldest = sessionAt("2026-09-01T10:00:00Z");
        Session middle = sessionAt("2026-09-01T11:00:00Z");
        Session newest = sessionAt("2026-09-01T12:00:00Z");
        Session justCreated = sessionAt("2026-09-01T13:00:00Z");
        when(repository.findByPrincipalName("user@example.com"))
                .thenReturn(Map.of("session-1", oldest, "session-2", middle, "session-3", newest,
                        "session-4", justCreated));

        assertThat(service.expireOldestBeyondLimit("user@example.com", 3)).isEqualTo(1);

        verify(repository).deleteById("session-1");
    }

    @Test
    void keepsExistingSessionsWhenTheNewLoginFitsWithinTheLimit() {
        Session existing = sessionAt("2026-09-01T10:00:00Z");
        when(repository.findByPrincipalName("user@example.com"))
                .thenReturn(Map.of("session-1", existing));

        assertThat(service.expireOldestBeyondLimit("user@example.com", 3)).isZero();
    }

    private Session sessionAt(String instant) {
        Session session = mock(Session.class);
        when(session.getCreationTime()).thenReturn(Instant.parse(instant));
        return session;
    }
}
