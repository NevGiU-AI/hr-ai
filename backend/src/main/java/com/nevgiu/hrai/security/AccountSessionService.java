package com.nevgiu.hrai.security;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

import java.util.Comparator;
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

    public int expireOldestBeyondLimit(String principalName, int maximumSessions) {
        Map<String, ? extends Session> activeSessions = sessions.findByPrincipalName(principalName);
        int sessionsToExpire = Math.max(0, activeSessions.size() - maximumSessions);
        activeSessions.entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<String, ? extends Session> entry) ->
                                entry.getValue().getCreationTime())
                        .thenComparing(Map.Entry::getKey))
                .limit(sessionsToExpire)
                .map(Map.Entry::getKey)
                .forEach(sessions::deleteById);
        return sessionsToExpire;
    }
}
