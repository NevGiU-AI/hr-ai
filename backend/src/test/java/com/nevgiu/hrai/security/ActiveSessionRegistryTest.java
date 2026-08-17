package com.nevgiu.hrai.security;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActiveSessionRegistryTest {
    private final ActiveSessionRegistry registry = new ActiveSessionRegistry();

    @Test
    void revokesEveryRegisteredSessionForAUser() {
        HttpSession first = mock(HttpSession.class);
        HttpSession second = mock(HttpSession.class);
        registry.register(7L, first);
        registry.register(7L, second);

        assertThat(registry.revoke(7L)).isEqualTo(2);
        verify(first).invalidate();
        verify(second).invalidate();
        assertThat(registry.revoke(7L)).isZero();
    }

    @Test
    void unregisterRemovesTheSessionFromFutureRevocation() {
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute(ActiveSessionRegistry.USER_ID_ATTRIBUTE)).thenReturn(7L);
        registry.register(7L, session);
        registry.unregister(session);

        assertThat(registry.revoke(7L)).isZero();
    }
}
