package com.nevgiu.hrai.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security.session-policy")
public record SessionPolicyProperties(int maximumSessions) {
    public SessionPolicyProperties {
        if (maximumSessions < 1) maximumSessions = 3;
    }
}
