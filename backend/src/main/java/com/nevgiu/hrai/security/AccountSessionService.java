package com.nevgiu.hrai.security;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AccountSessionService {
    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public AccountSessionService(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    public int revoke(String principalName) {
        Map<String, ? extends Session> activeSessions = sessions.findByPrincipalName(principalName);
        activeSessions.keySet().forEach(sessions::deleteById);
        return activeSessions.size();
    }
}
