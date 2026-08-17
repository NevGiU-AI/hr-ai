package com.nevgiu.hrai.security;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

public class ActiveSessionListener implements HttpSessionListener {
    private final ActiveSessionRegistry sessions;

    public ActiveSessionListener(ActiveSessionRegistry sessions) {
        this.sessions = sessions;
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        Object userId = event.getSession().getAttribute(ActiveSessionRegistry.USER_ID_ATTRIBUTE);
        if (userId instanceof Long id) sessions.remove(id, event.getSession());
    }
}
