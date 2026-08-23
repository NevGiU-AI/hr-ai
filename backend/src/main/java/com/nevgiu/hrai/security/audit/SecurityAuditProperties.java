package com.nevgiu.hrai.security.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties("app.security.audit")
public record SecurityAuditProperties(Duration retention) {
    public SecurityAuditProperties {
        if (retention == null || retention.isNegative() || retention.isZero()) retention = Duration.ofDays(365);
    }
}
