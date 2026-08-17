package com.nevgiu.hrai.security;

import org.junit.jupiter.api.Test;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

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
}
