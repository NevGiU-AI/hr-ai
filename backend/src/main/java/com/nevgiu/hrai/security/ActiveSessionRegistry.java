package com.nevgiu.hrai.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ActiveSessionRegistry {
    static final String USER_ID_ATTRIBUTE = ActiveSessionRegistry.class.getName() + ".userId";

    private final ConcurrentHashMap<Long, Set<HttpSession>> sessionsByUser = new ConcurrentHashMap<>();

    public void register(Long userId, HttpSession session) {
        session.setAttribute(USER_ID_ATTRIBUTE, userId);
        sessionsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregister(HttpSession session) {
        Object userId = session.getAttribute(USER_ID_ATTRIBUTE);
        if (userId instanceof Long id) remove(id, session);
    }

    public int revoke(Long userId) {
        Set<HttpSession> sessions = sessionsByUser.remove(userId);
        if (sessions == null) return 0;
        int revoked = 0;
        for (HttpSession session : sessions) {
            try {
                session.invalidate();
                revoked++;
            } catch (IllegalStateException ignored) {
                // The container already expired this session.
            }
        }
        return revoked;
    }

    void remove(Long userId, HttpSession session) {
        sessionsByUser.computeIfPresent(userId, (ignored, sessions) -> {
            sessions.remove(session);
            return sessions.isEmpty() ? null : sessions;
        });
    }
}
