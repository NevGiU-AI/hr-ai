package com.nevgiu.hrai.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.security.login-throttle")
public record LoginThrottleProperties(
        String namespace,
        int accountFailureLimit,
        int ipFailureLimit,
        Duration failureWindow,
        Duration lockDuration
) {
    public LoginThrottleProperties {
        if (namespace == null || namespace.isBlank()) namespace = "hr-ai:security:login";
        if (accountFailureLimit < 1) accountFailureLimit = 5;
        if (ipFailureLimit < 1) ipFailureLimit = 20;
        if (failureWindow == null || failureWindow.isNegative() || failureWindow.isZero()) {
            failureWindow = Duration.ofMinutes(15);
        }
        if (lockDuration == null || lockDuration.isNegative() || lockDuration.isZero()) {
            lockDuration = Duration.ofMinutes(15);
        }
    }
}
