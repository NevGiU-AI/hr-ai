package com.nevgiu.hrai.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security")
public record SecurityProperties(
        String bootstrapAdminEmail,
        String bootstrapAdminPassword,
        String bootstrapAdminOrganization
) {}
